package com.a207.runkk.avatar;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.SelfieLimit;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 회원 아바타의 외형 상태(설계 6.1.8, 상태도 2.4.12). 얼굴은 셀카 SD 부위(SelfieFace) 또는 기본 캐릭터(DefaultFace)다. 착용 아이템 보유
 * 확인은 AvatarService가 먼저 하고 Avatar는 보유 여부를 받지 않는다(design-review.md 9.2.1).
 */
@Entity
@Table(name = "avatar")
public class Avatar {

    /** 얼굴이 없을 때 안내문(FR-162). */
    static final String CHOOSE_DEFAULT_GUIDE = "기본 캐릭터를 고를 수 있다";

    @Id
    @Column(name = "member_id")
    private Long memberId;

    /** 기본 캐릭터 얼굴이면 그 캐릭터. 셀카 얼굴이면 null이고 facePartKeys가 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_character", length = 20)
    private DefaultCharacter defaultCharacter;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "avatar_face_part", joinColumns = @JoinColumn(name = "member_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "part", length = 10)
    @Column(name = "image_key", nullable = false, length = 200)
    private Map<FacePart, String> facePartKeys = new HashMap<>();

    @Column(name = "creation_count", nullable = false)
    private int creationCount;

    @Column(name = "last_created_on")
    private LocalDate lastCreatedOn;

    /** lastCreatedOn 날짜에 만든 횟수(SelfieLimit.perDay 비교용). */
    @Column(name = "created_on_last_day", nullable = false)
    private int createdOnLastDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_shape", nullable = false, length = 10)
    private BodyShape bodyShape;

    @Enumerated(EnumType.STRING)
    @Column(name = "skin_tone", nullable = false, length = 10)
    private SkinTone skinTone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "avatar_equipment", joinColumns = @JoinColumn(name = "member_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "slot", length = 10)
    @Column(name = "item_id", nullable = false)
    private Map<ItemSlot, Long> equipped = new HashMap<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Avatar() {}

    private Avatar(long memberId) {
        this.memberId = memberId;
        this.bodyShape = BodyShape.STAGE1;
        this.skinTone = SkinTone.DAYTIME;
    }

    /** 얼굴 없음, 1단계 체형, 낮 톤, 착용 없음. */
    public static Avatar create(long memberId) {
        return new Avatar(memberId);
    }

    /** 오늘 만든 횟수 &lt; limit.perDay(FR-157). */
    public boolean canCreateSelfie(LocalDate today, SelfieLimit limit) {
        int madeToday = today.equals(lastCreatedOn) ? createdOnLastDay : 0;
        return madeToday < limit.perDay();
    }

    /**
     * face = SelfieFace, 생성 횟수 + 1, lastCreatedOn = today. 오늘 한도를 이미 채웠으면
     * BusinessRuleException(SELFIE_LIMIT). 셀카 한도가 운영 기준값이라(SelfieLimit, D-41) 한도를 함께 받는다.
     */
    public void applySelfieFace(SdFaceParts parts, LocalDate today, SelfieLimit limit) {
        if (!canCreateSelfie(today, limit)) {
            throw new BusinessRuleException(ReasonCode.SELFIE_LIMIT);
        }
        createdOnLastDay = today.equals(lastCreatedOn) ? createdOnLastDay + 1 : 1;
        lastCreatedOn = today;
        creationCount++;
        defaultCharacter = null;
        facePartKeys.clear();
        facePartKeys.putAll(parts.parts());
    }

    /** face = DefaultFace(c). 셀카 얼굴이 있어도 바꿀 수 있다(FR-158). 생성 횟수는 그대로다. */
    public void chooseDefault(DefaultCharacter c) {
        defaultCharacter = Objects.requireNonNull(c, "기본 캐릭터");
        facePartKeys.clear();
    }

    /** 같은 부위 기존 아이템을 바꾼다. 보유 확인은 AvatarService가 먼저 한다. */
    public void equip(long itemId, ItemSlot slot) {
        equipped.put(slot, itemId);
    }

    public void unequip(ItemSlot slot) {
        equipped.remove(slot);
    }

    /** 체형이 바뀌면 true(알림 대상). */
    public boolean updateAppearance(BodyShape shape, SkinTone tone) {
        boolean changed = bodyShape != shape;
        bodyShape = shape;
        skinTone = tone;
        return changed;
    }

    /** 얼굴. 고르지 않았으면 비어 있다. */
    public Optional<Face> face() {
        if (defaultCharacter != null) {
            return Optional.of(new DefaultFace(defaultCharacter));
        }
        if (facePartKeys.size() == FacePart.values().length) {
            return Optional.of(new SelfieFace(new SdFaceParts(facePartKeys)));
        }
        return Optional.empty();
    }

    /** 얼굴이 없으면 "기본 캐릭터를 고를 수 있다". */
    public AvatarView view(int balance) {
        Optional<String> key = face().map(Face::renderKey);
        Map<ItemSlot, Long> worn = new EnumMap<>(ItemSlot.class);
        worn.putAll(equipped);
        return new AvatarView(
                key.orElse(null),
                key.isPresent() ? null : CHOOSE_DEFAULT_GUIDE,
                bodyShape,
                skinTone,
                worn,
                balance,
                false,
                Map.of());
    }

    public long memberId() {
        return memberId;
    }

    public int creationCount() {
        return creationCount;
    }

    public BodyShape bodyShape() {
        return bodyShape;
    }

    public SkinTone skinTone() {
        return skinTone;
    }
}
