package com.a207.runkk.member;

import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.TierBoundary;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 한 회원의 배치고사 진행(센 판, 임시 티어, 확정 시점)을 보관한다(05-21 2.1, D-475, 상태도 2.4.27). 표 placement_progress(회원당
 * 1행)와 placement_round(센 판마다 1행). 회원 행에 칼럼을 더하지 않아 티어 갱신과의 낙관적 잠금 충돌을 줄이고 탈퇴 때 행째 지운다.
 */
@Entity
@Table(name = "placement_progress")
public class PlacementProgress {

    /** 배치고사 판 수(브리프 규칙 2, 계약 policy/TierPlacement.txt의 원천, D-509). */
    public static final int ROUNDS = 3;

    /** 같은 대결을 이미 셌을 때의 Ignored 사유. */
    public static final String DUPLICATE = "duplicate";

    @Id
    @Column(name = "member_id")
    private long memberId;

    @Column(name = "counted_rounds", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int countedRounds;

    @Enumerated(EnumType.STRING)
    @Column(name = "provisional_tier", length = 10)
    private Tier provisionalTier;

    @Column(name = "confirmed_run_ordinal")
    private Integer confirmedRunOrdinal;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 센 판. 순서 칼럼 없는 bag이라 바뀔 때마다 그 회원의 판 행을 지우고 다시 넣는다(최대 3행). 바꾸는 경로는 모두 진행 행을 PESSIMISTIC_WRITE로
     * 잠근 뒤 부른다(countRound·correctVerdict, D-516). 잠금 없이 저장하는 경로를 만들지 않는다(리뷰 M4).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "placement_round",
            joinColumns = @JoinColumn(name = "member_id"),
            uniqueConstraints =
                    @UniqueConstraint(
                            name = "uq_placement_round_battle",
                            columnNames = "battle_id"))
    @OrderBy("round ASC")
    private List<PlacementRound> rounds = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PlacementProgress() {}

    private PlacementProgress(long memberId) {
        this.memberId = memberId;
    }

    /** 0판에서 시작하는 진행. */
    public static PlacementProgress start(long memberId) {
        return new PlacementProgress(memberId);
    }

    /** 다음 판 번호. 완료면 IllegalStateException. */
    public int nextRound() {
        if (isCompleted()) {
            throw new IllegalStateException("배치고사를 이미 마쳤다: " + memberId);
        }
        return countedRounds + 1;
    }

    /** 대결을 만들 때의 진행(다음 판, 임시 티어). */
    public PlacementBasis basis() {
        return new PlacementBasis(nextRound(), provisionalTier);
    }

    public Optional<Tier> provisionalTier() {
        return Optional.ofNullable(provisionalTier);
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean hasCounted(long battleId) {
        return rounds.stream().anyMatch(r -> r.battleId() == battleId);
    }

    /**
     * A-37. 완료이거나 같은 대결이면 Ignored. 아니면 판을 더하고(번호 = 센 판 수 + 1, D-483) 센 판 점수 평균의 티어를 낸다. 1·2판이면 임시
     * 티어, 3판이면 확정(임시 티어 비움, 완료 시각·확정 판 순번 남김).
     */
    public PlacementCount count(PlacementRoundInput in, TierBoundary boundary) {
        if (isCompleted()) {
            return new PlacementCount.Ignored("completed");
        }
        if (hasCounted(in.battleId())) {
            return new PlacementCount.Ignored(DUPLICATE);
        }
        int round = countedRounds + 1;
        rounds.add(PlacementRound.of(round, in));
        countedRounds = round;
        double average = rounds.stream().mapToDouble(PlacementRound::score).average().orElseThrow();
        Tier tier = boundary.tierOf(average);
        if (round < ROUNDS) {
            provisionalTier = tier;
            return new PlacementCount.Provisional(round, tier);
        }
        provisionalTier = null;
        completedAt = in.countedAt();
        confirmedRunOrdinal = in.validRunOrdinal();
        return new PlacementCount.Confirmed(tier);
    }

    /** 판정 정정(BattleJudged.corrected). 그 대결을 센 판이 있으면 승패를 바꾸고 참. */
    public boolean correctVerdict(long battleId, PlacementVerdict verdict) {
        for (int i = 0; i < rounds.size(); i++) {
            if (rounds.get(i).battleId() == battleId) {
                rounds.set(i, rounds.get(i).withVerdict(verdict));
                return true;
            }
        }
        return false;
    }

    /** 확정 판 기록의 유효 러닝 순번(재평가 기준, D-484). 확정 전이면 비어 있다. */
    public OptionalInt confirmedRunOrdinal() {
        return confirmedRunOrdinal == null
                ? OptionalInt.empty()
                : OptionalInt.of(confirmedRunOrdinal);
    }

    public Optional<LocalDateTime> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public int countedRounds() {
        return countedRounds;
    }

    /** 센 판(round 오름차순)의 불변 사본. */
    public List<PlacementRound> rounds() {
        return rounds.stream().sorted(Comparator.comparingInt(PlacementRound::round)).toList();
    }

    public long getMemberId() {
        return memberId;
    }
}
