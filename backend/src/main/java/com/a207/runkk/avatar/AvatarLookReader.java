package com.a207.runkk.avatar;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.AvatarLookSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 회원의 지금 모습을 읽어 common.AvatarLook으로 돌려준다(설계 5.31 B, [임시:D-성-248]). 성별은 다른 러너에게 보이는 표시
 * 성별(MemberQueryService.shownGendersOf), 옷은 Avatar의 착용 아이템(TOP·BOTTOM·SHOES), 체형과 러닝량은 내 러너
 * 조회(RunnerService)와 같은 값, 얼굴은 Face.renderKey다. 한 읽기 트랜잭션에서 읽는다.
 */
@Service
class AvatarLookReader implements AvatarLookSource {

    /** 모습에 싣는 부위(D-성-246). 모자(HAT)는 싣지 않는다. */
    private static final Set<ItemSlot> LOOK_SLOTS =
            Set.of(ItemSlot.TOP, ItemSlot.BOTTOM, ItemSlot.SHOES);

    private final AvatarRepository avatarRepository;
    private final RunnerService runnerService;
    private final MemberQueryService memberQueryService;
    private final AvatarTransactions transactions;

    AvatarLookReader(
            AvatarRepository avatarRepository,
            RunnerService runnerService,
            MemberQueryService memberQueryService,
            AvatarTransactions transactions) {
        this.avatarRepository = avatarRepository;
        this.runnerService = runnerService;
        this.memberQueryService = memberQueryService;
        this.transactions = transactions;
    }

    @Override
    public Optional<AvatarLook> currentLookOf(long memberId) {
        return Optional.of(transactions.read(() -> read(memberId)));
    }

    private AvatarLook read(long memberId) {
        Gender gender = memberQueryService.shownGendersOf(List.of(memberId)).get(memberId);
        AvatarView avatar = avatarRepository.findOrCreate(memberId).view(0);
        RunnerView runner = runnerService.runner(memberId);
        Map<ItemSlot, Long> equipped = new EnumMap<>(ItemSlot.class);
        avatar.equipped()
                .forEach(
                        (slot, itemId) -> {
                            if (LOOK_SLOTS.contains(slot)) {
                                equipped.put(slot, itemId);
                            }
                        });
        return new AvatarLook(
                gender.name(),
                equipped,
                runner.bodyVariant(),
                runner.runAmount(),
                avatar.faceKey());
    }
}
