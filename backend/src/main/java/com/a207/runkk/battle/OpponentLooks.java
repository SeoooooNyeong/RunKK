package com.a207.runkk.battle;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.AvatarLookSource;
import com.a207.runkk.running.RunQueryService;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대결 상대의 아바타 모습(설계 5.31 B, [임시:D-성-251]~[임시:D-성-253]). 성사 알림(MatchFound), 대결 조회, 대결 결과, 기록 대결 후보와
 * 시작 응답이 같은 규칙을 쓰도록 한곳에 둔다.
 *
 * <ul>
 *   <li>랜덤·친구 대결: 상대 회원의 지금 모습에, 대결에 저장된 표시 성별(OpponentGenders.nameFor)을 덮어쓴다.
 *   <li>나와의 싸움: 상대 기록을 달린 날의 내 모습, 모습이 없는 옛 기록이면 지금 내 모습. 본인만 보므로 성별은 표시 성별이 아니라 내 실제 성별(입력한 남·여)로
 *       덮어쓴다. 성별을 입력하지 않았으면 그대로 둔다.
 *   <li>고스트(러너 기록·가상 기록), 배치고사, 유명인: 모습이 없다(null).
 * </ul>
 *
 * <p>모습을 읽다가 실패하면 경고 로그만 남기고 null을 싣는다(S15P31A207-306, PM 결정: 못 읽으면 성별 기본 아바타). 모습 때문에 매칭 성사나 조회가
 * 실패하지 않는다. 읽기는 AvatarLookSource와 RunQueryService.lookOf가 부르는 쪽 트랜잭션과 떨어진 트랜잭션에서 하므로 여기서 예외를 잡아도 대결
 * 트랜잭션이 rollback-only가 되지 않는다.
 */
@Component
class OpponentLooks {

    private static final Logger log = LoggerFactory.getLogger(OpponentLooks.class);

    /** 풀 크기를 모를 때의 기준(HikariCP 기본 풀 크기). */
    private static final int DEFAULT_POOL_SIZE = 10;

    /** 모습 읽기가 동시에 쓸 수 있는 연결은 풀의 1/5까지다. */
    private static final int POOL_SHARE = 5;

    private final AvatarLookSource avatarLookSource;
    private final RunQueryService runQueryService;

    /** 본인에게 보이는 내 성별 이름(MemberQueryService.ownGenderOf), 입력하지 않았으면 비어 있다. */
    private final LongFunction<Optional<String>> ownGender;

    /**
     * 동시에 도는 모습 읽기 수의 상한. 대결 서비스는 자기 트랜잭션의 연결을 쥔 채 모습을 읽고, 그 읽기가 연결을 하나 더 쓴다. 모든 요청이 연결 하나씩을 쥐고 두
     * 번째를 기다리면 풀이 멈추므로(HikariCP 연결 대기 30초), 상한을 풀 크기보다 작게 두고 자리가 없으면 기다리지 않고 모습 없이(null) 보낸다.
     */
    private final Semaphore readSlots;

    @Autowired
    OpponentLooks(
            AvatarLookSource avatarLookSource,
            RunQueryService runQueryService,
            MemberQueryService memberQueryService,
            @Value("${spring.datasource.hikari.maximum-pool-size:" + DEFAULT_POOL_SIZE + "}")
                    int poolSize) {
        this(
                avatarLookSource,
                runQueryService,
                memberId -> memberQueryService.ownGenderOf(memberId).map(Enum::name),
                poolSize);
    }

    OpponentLooks(
            AvatarLookSource avatarLookSource,
            RunQueryService runQueryService,
            LongFunction<Optional<String>> ownGender,
            int poolSize) {
        this.avatarLookSource = avatarLookSource;
        this.runQueryService = runQueryService;
        this.ownGender = ownGender;
        this.readSlots = new Semaphore(Math.max(1, poolSize / POOL_SHARE));
    }

    OpponentLooks(
            AvatarLookSource avatarLookSource,
            RunQueryService runQueryService,
            LongFunction<Optional<String>> ownGender) {
        this(avatarLookSource, runQueryService, ownGender, DEFAULT_POOL_SIZE);
    }

    OpponentLooks(
            AvatarLookSource avatarLookSource, RunQueryService runQueryService, int poolSize) {
        this(avatarLookSource, runQueryService, memberId -> Optional.empty(), poolSize);
    }

    OpponentLooks(AvatarLookSource avatarLookSource, RunQueryService runQueryService) {
        this(avatarLookSource, runQueryService, DEFAULT_POOL_SIZE);
    }

    /** 회원이 보는 상대 모습, 싣지 않거나 읽지 못하면 null. */
    AvatarLook of(Battle battle, long memberId) {
        if (battle.getType() == BattleType.SELF) {
            return battle.getOpponent().map(ref -> selfLook(memberId, ref.recordId())).orElse(null);
        }
        if (battle.getOpponent().isPresent() || !battle.getType().hasLiveRival()) {
            return null;
        }
        String shown = OpponentGenders.nameFor(battle, memberId);
        return battle.rivalOf(memberId)
                .flatMap(rival -> currentLook(rival.getMemberId()))
                .map(look -> shown == null ? look : look.withGender(shown))
                .orElse(null);
    }

    /** 나와의 싸움 상대(그 기록을 달린 날의 나). 기록에 모습이 없거나 못 읽으면 지금 모습으로 대신한다. 둘 다 없으면 null. */
    AvatarLook selfLook(long memberId, String recordId) {
        return recordLook(recordId)
                .or(() -> currentLook(memberId))
                .map(look -> ownGenderOf(memberId).map(look::withGender).orElse(look))
                .orElse(null);
    }

    /** 내 실제 성별. 읽다가 실패하면 경고 로그만 남기고 비어 있다(모습 때문에 대결이 실패하지 않는다). */
    private Optional<String> ownGenderOf(long memberId) {
        return guarded("own_gender", memberId, () -> ownGender.apply(memberId));
    }

    private Optional<AvatarLook> recordLook(String recordId) {
        return guarded("record", recordId, () -> runQueryService.lookOf(recordId));
    }

    private Optional<AvatarLook> currentLook(long memberId) {
        return guarded("current", memberId, () -> avatarLookSource.currentLookOf(memberId));
    }

    /** 자리가 있을 때만 읽는다. 자리가 없거나 읽다가 실패하면 경고 로그만 남기고 비어 있다. */
    private <T> Optional<T> guarded(String source, Object id, Supplier<Optional<T>> read) {
        if (!readSlots.tryAcquire()) {
            log.warn("battle_look_read_skipped source={} id={} reason=busy", source, id);
            return Optional.empty();
        }
        try {
            return read.get();
        } catch (RuntimeException e) {
            log.warn(
                    "battle_look_read_failed source={} id={} error={}",
                    source,
                    id,
                    e.getClass().getSimpleName(),
                    e);
            return Optional.empty();
        } finally {
            readSlots.release();
        }
    }
}
