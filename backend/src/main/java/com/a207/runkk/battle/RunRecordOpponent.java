package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.RunComparison;
import com.a207.runkk.running.RunCurve;
import java.util.List;
import java.util.Objects;

/**
 * 러너 기록 상대: 고스트(다른 러너의 전체공개 기록)·나와의 싸움(본인 최고 기록)(설계 6.1.6, D-51). 표는 RunQueryService.curve의 1초
 * 표본(좌표 없음, FR-089), 비교 값은 RunQueryService.comparisonOf다. RunRecord 엔티티를 갖지 않는다(D-32).
 */
public final class RunRecordOpponent implements OpponentRecord {

    private final String recordId;
    private final RunComparison comparison;
    private final RunCurve curve;
    private final Owner owner;

    /**
     * @param recordId 러닝 기록 ID
     * @param comparison 비교 값
     * @param curve 경과 시간·누적 거리 표본
     * @param owner 기록 주인 표시명(나와의 싸움은 "MM/DD의 나")·티어
     */
    public RunRecordOpponent(
            String recordId, RunComparison comparison, RunCurve curve, Owner owner) {
        this.recordId = Objects.requireNonNull(recordId, "recordId");
        this.comparison = Objects.requireNonNull(comparison, "comparison");
        this.curve = Objects.requireNonNull(curve, "curve");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * 기록 주인의 표시 값. 고스트는 GhostOwner, 나와의 싸움은 본인 MemberSummary에서 채운다.
     *
     * @param name 표시명
     * @param tier 티어
     */
    public record Owner(DisplayName name, Tier tier) {}

    @Override
    public OpponentTrack track(Distance battleDistance) {
        List<TrackSample> samples =
                curve.samples().stream()
                        .filter(p -> !p.elapsed().isZero())
                        .map(RunRecordOpponent::sampleOf)
                        .toList();
        return OpponentTrack.of(samples).cut(battleDistance);
    }

    private static TrackSample sampleOf(CurvePoint point) {
        return new TrackSample(point.elapsed(), point.distance());
    }

    @Override
    public DisplayName displayName() {
        return owner.name();
    }

    @Override
    public Tier displayTier() {
        return owner.tier();
    }

    @Override
    public OpponentKind kind() {
        return OpponentKind.RUN_RECORD;
    }

    @Override
    public String recordId() {
        return recordId;
    }

    @Override
    public ComparisonValues comparisonValues(Distance battleDistance) {
        return ComparisonValues.of(comparison);
    }
}
