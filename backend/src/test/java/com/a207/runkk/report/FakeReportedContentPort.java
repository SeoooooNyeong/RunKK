package com.a207.runkk.report;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 시험용 ReportedContentPort. 맡은 종류의 콘텐츠를 지도에 두고 hide·unhide 호출을 적어 둔다. */
class FakeReportedContentPort implements ReportedContentPort {

    private final Set<ReportTargetKind> kinds;
    private final Map<String, ReportedContent> contents = new HashMap<>();
    final List<String> hides = new ArrayList<>();
    final List<String> unhides = new ArrayList<>();

    FakeReportedContentPort(ReportTargetKind first, ReportTargetKind... rest) {
        this.kinds = EnumSet.of(first, rest);
    }

    private static String key(ReportTargetKind kind, long targetId) {
        return kind + ":" + targetId;
    }

    void put(ReportTargetKind kind, long targetId, ReportedContent content) {
        contents.put(key(kind, targetId), content);
    }

    void remove(ReportTargetKind kind, long targetId) {
        contents.remove(key(kind, targetId));
    }

    @Override
    public boolean supports(ReportTargetKind kind) {
        return kinds.contains(kind);
    }

    @Override
    public Optional<ReportedContent> find(ReportTargetKind kind, long targetId) {
        return Optional.ofNullable(contents.get(key(kind, targetId)));
    }

    @Override
    public void hide(ReportTargetKind kind, long targetId) {
        hides.add(key(kind, targetId));
        contents.computeIfPresent(
                key(kind, targetId),
                (k, c) ->
                        new ReportedContent(
                                c.crewId(), c.authorId(), c.preview(), true, c.crewMemberIds()));
    }

    @Override
    public void unhide(ReportTargetKind kind, long targetId) {
        unhides.add(key(kind, targetId));
        contents.computeIfPresent(
                key(kind, targetId),
                (k, c) ->
                        new ReportedContent(
                                c.crewId(), c.authorId(), c.preview(), false, c.crewMemberIds()));
    }
}
