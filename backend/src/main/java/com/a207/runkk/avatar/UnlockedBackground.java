package com.a207.runkk.avatar;

import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.LandmarkStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 회원이 해금한 랜드마크 배경 하나를 해금 때의 모습으로 보관한다(설계 6.1.8, FR-201, D-107). 운영 기준값(LANDMARKS)이 바뀌어도 그대로인 스냅숏이고
 * (회원, 키)가 기본 키다. 연산은 없고 Avatar.unlock만 만든다.
 */
@Entity
@Table(name = "avatar_background")
@IdClass(UnlockedBackground.Key.class)
public class UnlockedBackground {

    /** landmark_name 칼럼 길이(V25). 더 긴 이름은 잘라 스냅숏으로 남긴다. */
    static final int NAME_MAX = 40;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Column(name = "landmark_key", length = 40)
    private String landmarkKey;

    @Column(name = "landmark_name", nullable = false, length = NAME_MAX)
    private String landmarkName;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private LandmarkStage stage;

    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    protected UnlockedBackground() {}

    private UnlockedBackground(long memberId, Landmark landmark, LocalDateTime unlockedAt) {
        this.memberId = memberId;
        this.landmarkKey = landmark.key();
        this.landmarkName =
                landmark.name().length() > NAME_MAX
                        ? landmark.name().substring(0, NAME_MAX)
                        : landmark.name();
        this.stage = landmark.stage();
        this.distanceMeters = landmark.distance().meters();
        this.unlockedAt = unlockedAt;
    }

    static UnlockedBackground of(long memberId, Landmark landmark, LocalDateTime unlockedAt) {
        return new UnlockedBackground(memberId, landmark, unlockedAt);
    }

    public String landmarkKey() {
        return landmarkKey;
    }

    public String landmarkName() {
        return landmarkName;
    }

    public LandmarkStage stage() {
        return stage;
    }

    public Distance distance() {
        return Distance.ofMeters(distanceMeters);
    }

    public LocalDateTime unlockedAt() {
        return unlockedAt;
    }

    /** JPA 복합 기본 키 (member_id, landmark_key). */
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long memberId;
        private String landmarkKey;

        protected Key() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k
                    && Objects.equals(memberId, k.memberId)
                    && Objects.equals(landmarkKey, k.landmarkKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberId, landmarkKey);
        }
    }
}
