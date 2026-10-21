package com.a207.runkk.avatar;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.SelfieLimit;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 셀카 한 장을 SD 얼굴로 바꿔 아바타에 적용한다(SQ-11, 5.3, D-59). AvatarService.createFromSelfie의 변환 단계를 맡는 패키지 전용
 * 보조다. 순서: 하루 한도 검사(짧은 읽기) → 회원별 진행 중 잠금(SET NX 30초) → 변환(잠금 없이, 최대 수 초) → 행 잠금·재검사·부위 이미지 저장·적용(짧은
 * 쓰기). 셀카 바이트는 매개변수로만 다루고 저장·로그하지 않는다(FR-156). 실패는 생성 횟수를 쓰지 않으므로 하루 실패 5번에서 멈춘다.
 */
@Component
class SelfieConversion {

    /** 진행 중 잠금 유지 시간(5.3). 변환 읽기 시간 제한(15초)보다 길다. */
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** 하루 변환 실패 상한(B4 리뷰: ai 반복 호출 방지). 넘으면 SELFIE_RETRY_LIMIT. */
    static final int MAX_FAILURES_PER_DAY = 5;

    private static final Logger log = LoggerFactory.getLogger(SelfieConversion.class);

    private final FaceTransformGateway faceTransformGateway;
    private final FacePartImageStore facePartImageStore;
    private final SelfieLockStore selfieLockStore;
    private final SelfieFailureStore selfieFailureStore;
    private final AvatarRepository avatarRepository;
    private final MemberQueryService memberQueryService;
    private final AvatarTransactions transactions;

    SelfieConversion(
            FaceTransformGateway faceTransformGateway,
            FacePartImageStore facePartImageStore,
            SelfieLockStore selfieLockStore,
            SelfieFailureStore selfieFailureStore,
            AvatarRepository avatarRepository,
            MemberQueryService memberQueryService,
            AvatarTransactions transactions) {
        this.faceTransformGateway = faceTransformGateway;
        this.facePartImageStore = facePartImageStore;
        this.selfieLockStore = selfieLockStore;
        this.selfieFailureStore = selfieFailureStore;
        this.avatarRepository = avatarRepository;
        this.memberQueryService = memberQueryService;
        this.transactions = transactions;
    }

    /**
     * 성공이면 비어 있고 변환 실패면 사유(생성 횟수 그대로, FR-155). 오늘 한도를 채웠으면 SELFIE_LIMIT, 같은 회원이 이미 변환 중이면
     * SELFIE_IN_PROGRESS.
     */
    Optional<FailureReason> convert(
            long memberId, byte[] selfie, LocalDate today, SelfieLimit limit) {
        boolean allowed =
                transactions.read(
                        () ->
                                avatarRepository
                                        .findOrCreate(memberId)
                                        .canCreateSelfie(today, limit));
        if (!allowed) {
            throw new BusinessRuleException(ReasonCode.SELFIE_LIMIT);
        }
        if (selfieFailureStore.failuresOn(memberId, today) >= MAX_FAILURES_PER_DAY) {
            throw new BusinessRuleException(ReasonCode.SELFIE_RETRY_LIMIT);
        }
        String token =
                selfieLockStore
                        .tryLock(memberId, LOCK_TTL)
                        .orElseThrow(
                                () -> new BusinessRuleException(ReasonCode.SELFIE_IN_PROGRESS));
        try {
            FaceTransformOutcome outcome = faceTransformGateway.transform(selfie);
            if (!outcome.isSuccess()) {
                log.info("selfie_failed reason={}", outcome.failure());
                selfieFailureStore.recordFailure(memberId, today);
                return outcome.failureReason();
            }
            transactions.write(() -> apply(memberId, outcome.images(), today, limit));
            return Optional.empty();
        } finally {
            selfieLockStore.unlock(memberId, token);
        }
    }

    private Avatar apply(
            long memberId, SdFacePartImages images, LocalDate today, SelfieLimit limit) {
        Avatar avatar = avatarRepository.findForUpdate(memberId).orElseThrow();
        if (!memberQueryService.hasConsent(memberId, ConsentItem.FACE_TRANSFER)) {
            // 변환하는 사이 탈퇴(동의가 모두 지워짐)하거나 동의를 거뒀다: 부위 이미지를 남기지 않고 되돌린다(B4 리뷰 경합).
            // 행을 잠근 뒤 확인하므로, 이 뒤에 탈퇴하면 AvatarWithdrawalListener가 잠금이 풀린 뒤 이 이미지까지 지운다.
            throw new BusinessRuleException(ReasonCode.FACE_CONSENT_REQUIRED);
        }
        if (!avatar.canCreateSelfie(today, limit)) {
            throw new BusinessRuleException(ReasonCode.SELFIE_LIMIT);
        }
        SdFaceParts parts = facePartImageStore.save(memberId, images);
        avatar.applySelfieFace(parts, today, limit);
        avatarRepository.save(avatar);
        return avatar;
    }
}
