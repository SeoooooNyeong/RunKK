package com.a207.runkk.avatar;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.SelfieLimit;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    @Column(name = "head_job_id", length = 64)
    private String headJobId;

    @Column(name = "pending_face_request", length = 64)
    private String pendingFaceRequest;

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

    /** 고른 랜드마크 배경 키. null이면 기본 배경(FR-202, D-107 (5)). */
    @Column(name = "selected_background", length = 40)
    private String selectedBackgroundKey;

    /** 해금한 배경(avatar_background). 해금은 취소하지 않는다(D-107 (4)). 배경 연산 때만 읽는다. */
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    private List<UnlockedBackground> unlockedBackgrounds = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Avatar() {}

    private Avatar(long memberId) {
        this.memberId = memberId;
        this.bodyShape = BodyShape.STAGE4;
        this.skinTone = SkinTone.DAYTIME;
    }

    /** 얼굴 없음, 기본 몸(4단계, [임시:D-성-179]), 낮 톤, 착용 없음. */
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
        countSelfie(today, limit);
        headJobId = null;
        defaultCharacter = null;
        facePartKeys.clear();
        facePartKeys.putAll(parts.parts());
    }

    private void countSelfie(LocalDate today, SelfieLimit limit) {
        if (!canCreateSelfie(today, limit)) {
            throw new BusinessRuleException(ReasonCode.SELFIE_LIMIT);
        }
        createdOnLastDay = today.equals(lastCreatedOn) ? createdOnLastDay + 1 : 1;
        lastCreatedOn = today;
        creationCount++;
    }

    void beginFaceJob(String requestId) {
        pendingFaceRequest = requestId;
    }

    /** 이 셀카 요청의 결과를 아직 얼굴에 반영할 수 있는가(그 뒤 기본 캐릭터를 고르거나 새 셀카를 보내지 않았다). */
    boolean awaitsFaceJob(String requestId) {
        return requestId.equals(pendingFaceRequest);
    }

    /** 진행 중이던 셀카 요청을 반영하지 않고 끝낸다(한도를 넘어 결과를 버릴 때). */
    void endFaceJob(String requestId) {
        if (requestId.equals(pendingFaceRequest)) pendingFaceRequest = null;
    }

    void applyGeneratedHead(String requestId, String jobId, LocalDate today, SelfieLimit limit) {
        countSelfie(today, limit);
        if (requestId.equals(pendingFaceRequest)) {
            headJobId = jobId;
            defaultCharacter = null;
            facePartKeys.clear();
            pendingFaceRequest = null;
        }
    }

    /** face = DefaultFace(c). 셀카 얼굴이 있어도 바꿀 수 있다(FR-158). 생성 횟수는 그대로다. */
    public void chooseDefault(DefaultCharacter c) {
        defaultCharacter = Objects.requireNonNull(c, "기본 캐릭터");
        facePartKeys.clear();
        headJobId = null;
        pendingFaceRequest = null;
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

    /**
     * reached 중 아직 해금하지 않은 키만 해금 때의 키·이름·단계·거리 스냅숏(unlockedAt = now)으로 더하고 새로 해금한 것만 돌려준다. 해금은 취소하지
     * 않는다(기준값이 바뀌어도 지우지 않는다, FR-201, D-107).
     */
    public List<UnlockedBackground> unlock(Collection<Landmark> reached, LocalDateTime now) {
        Set<String> have = unlockedKeys();
        List<UnlockedBackground> added = new ArrayList<>();
        for (Landmark landmark : reached) {
            if (have.add(landmark.key())) {
                UnlockedBackground background = UnlockedBackground.of(memberId, landmark, now);
                unlockedBackgrounds.add(background);
                added.add(background);
            }
        }
        return added;
    }

    /** 해금하지 않은 키면 BusinessRuleException(BACKGROUND_LOCKED)이고 선택은 그대로다(FR-202). */
    public void selectBackground(String key) {
        if (key == null || !unlockedKeys().contains(key)) {
            throw new BusinessRuleException(ReasonCode.BACKGROUND_LOCKED);
        }
        selectedBackgroundKey = key;
    }

    /** 기본 배경으로 돌아간다(FR-202). */
    public void useDefaultBackground() {
        selectedBackgroundKey = null;
    }

    private Set<String> unlockedKeys() {
        Set<String> keys = new HashSet<>();
        unlockedBackgrounds.forEach(u -> keys.add(u.landmarkKey()));
        return keys;
    }

    /** 해금한 배경(읽기 전용 복사본). */
    public List<UnlockedBackground> unlockedBackgrounds() {
        return List.copyOf(unlockedBackgrounds);
    }

    /** 고른 배경 키. 기본 배경이면 비어 있다. */
    public Optional<String> selectedBackgroundKey() {
        return Optional.ofNullable(selectedBackgroundKey);
    }

    /** 얼굴. 고르지 않았으면 비어 있다. */
    public Optional<Face> face() {
        if (defaultCharacter != null) {
            return Optional.of(new DefaultFace(defaultCharacter));
        }
        if (headJobId != null) {
            return Optional.of(() -> "head:" + memberId + ":" + headJobId);
        }
        if (facePartKeys.size() == FacePart.values().length) {
            return Optional.of(new SelfieFace(new SdFaceParts(facePartKeys)));
        }
        return Optional.empty();
    }

    /** 얼굴이 없으면 "기본 캐릭터를 고를 수 있다". 고른 배경 키(null = 기본 배경)를 싣는다(FR-162, FR-202). */
    public AvatarView view(int balance) {
        Optional<String> key = face().map(Face::renderKey);
        Map<ItemSlot, Long> worn = new EnumMap<>(ItemSlot.class);
        worn.putAll(equipped);
        return new AvatarView(
                key.orElse(null),
                key.isPresent() ? null : CHOOSE_DEFAULT_GUIDE,
                bodyShape,
                skinTone,
                selectedBackgroundKey,
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
