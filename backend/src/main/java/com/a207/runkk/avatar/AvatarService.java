package com.a207.runkk.avatar;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.ShopCatalog;
import com.a207.runkk.reward.RewardService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아바타 얼굴·꾸미기 유스케이스(설계 6.1.8, SQ-11, srs.md 6.3 6.3·6.6). 셀카 변환은 트랜잭션 밖에서 SelfieConversion이 하고(5.3),
 * 잔액·보유 확인은 reward.RewardService로 읽는다(D-33). 조회(view)는 배포 전에 넘은 랜드마크 배경을 알림 없이 먼저 채운다(D-107 (3)).
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    private final AvatarRepository avatarRepository;
    private final SelfieConversion selfieConversion;
    private final RewardService rewardService;
    private final MemberQueryService memberQueryService;
    private final OperationPolicy operationPolicy;
    private final BackgroundService backgroundService;
    private final AvatarTransactions transactions;
    private final Clock clock;

    AvatarService(
            AvatarRepository avatarRepository,
            SelfieConversion selfieConversion,
            RewardService rewardService,
            MemberQueryService memberQueryService,
            OperationPolicy operationPolicy,
            BackgroundService backgroundService,
            AvatarTransactions transactions,
            Clock clock) {
        this.avatarRepository = avatarRepository;
        this.selfieConversion = selfieConversion;
        this.rewardService = rewardService;
        this.memberQueryService = memberQueryService;
        this.operationPolicy = operationPolicy;
        this.backgroundService = backgroundService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * SQ-11. 축소 운영(SELFIE_DISABLED) → 동의(FACE_CONSENT_REQUIRED) → 하루 한도(SELFIE_LIMIT) → 진행 중
     * 잠금(SELFIE_IN_PROGRESS) → 변환. 실패면 사유 + 기본 캐릭터 안내(횟수 그대로, FR-155). selfie는 어디에도 저장하지 않는다(D-59).
     */
    public AvatarResult createFromSelfie(long memberId, byte[] selfie) {
        if (!operationPolicy.features().selfieEnabled()) {
            throw new BusinessRuleException(ReasonCode.SELFIE_DISABLED);
        }
        if (!memberQueryService.hasConsent(memberId, ConsentItem.FACE_TRANSFER)) {
            throw new BusinessRuleException(ReasonCode.FACE_CONSENT_REQUIRED);
        }
        Optional<FailureReason> failure =
                selfieConversion.convert(
                        memberId, selfie, LocalDate.now(clock), operationPolicy.selfieLimit());
        AvatarView view = view(memberId);
        return failure.map(reason -> AvatarResult.failed(reason, view))
                .orElseGet(() -> AvatarResult.created(view));
    }

    /** 기본 캐릭터 4종 중 하나(enum이 보장). 셀카 얼굴이 있어도 바꿀 수 있다(FR-158). */
    @Transactional
    public AvatarResult chooseDefault(long memberId, DefaultCharacter c) {
        Avatar avatar = avatarRepository.findForUpdate(memberId).orElseThrow();
        avatar.chooseDefault(c);
        avatarRepository.save(avatar);
        return AvatarResult.created(viewOf(avatar));
    }

    /**
     * 변경마다: 착용이면 보유하고 상점 목록의 부위가 같을 때만 Avatar.equip, 아니면 그 부위를 ITEM_NOT_OWNED로 거부한다. 해제면
     * Avatar.unequip. 거부된 항목은 사유로 모은다(srs.md 6.3 6.6).
     */
    @Transactional
    public AvatarView equip(long memberId, List<EquipChange> changes) {
        Avatar avatar = avatarRepository.findForUpdate(memberId).orElseThrow();
        ShopCatalog catalog = operationPolicy.shopCatalog();
        Map<ItemSlot, ReasonCode> rejected = new EnumMap<>(ItemSlot.class);
        for (EquipChange change : changes) {
            Optional<Long> item = change.item();
            if (item.isEmpty()) {
                avatar.unequip(change.slot());
            } else if (wearable(memberId, item.get(), change.slot(), catalog)) {
                avatar.equip(item.get(), change.slot());
            } else {
                rejected.put(change.slot(), ReasonCode.ITEM_NOT_OWNED);
            }
        }
        avatarRepository.save(avatar);
        return viewOf(avatar).withRejected(rejected);
    }

    private boolean wearable(long memberId, long itemId, ItemSlot slot, ShopCatalog catalog) {
        return catalog.slotOf(itemId).filter(s -> s == slot).isPresent()
                && rewardService.owns(memberId, itemId);
    }

    /**
     * 잔액은 RewardService.balanceOf로 붙이고 고른 배경 키를 싣는다. 먼저 BackgroundService.backfillReached로 배포 전에 넘은
     * 랜드마크 해금을 알림 없이 채운다(GET /avatar, D-107 (3)). 채우기 실패는 로그만 남기고 조회는 계속한다(보정 배치가 채운다).
     */
    public AvatarView view(long memberId) {
        try {
            backgroundService.backfillReached(memberId, LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.warn(
                    "background_backfill_failed memberId={} error={}",
                    memberId,
                    e.getClass().getSimpleName(),
                    e);
        }
        return transactions.read(() -> viewOf(avatarRepository.findOrCreate(memberId)));
    }

    private AvatarView viewOf(Avatar avatar) {
        boolean selfieToday =
                operationPolicy.features().selfieEnabled()
                        && avatar.canCreateSelfie(
                                LocalDate.now(clock), operationPolicy.selfieLimit());
        return avatar.view(rewardService.balanceOf(avatar.memberId())).withSelfieToday(selfieToday);
    }
}
