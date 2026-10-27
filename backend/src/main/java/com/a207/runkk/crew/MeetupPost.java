package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 대면 모임 일정 하나의 참석 응답과 정원(설계 6.1.7, FR-132, FR-145, D-99). 장소는 이름만(지도 좌표 없음)이고 대기실·동시 출발·중계가 없다.
 * 작성자는 글을 쓸 때 GOING으로 들어가 정원에 든다(D-151). 동시 응답은 CrewPost.version 낙관적 잠금으로 막고 서비스가 다시 시도한다(5.3).
 */
@Entity
@DiscriminatorValue("MEETUP")
public class MeetupPost extends SchedulePost {

    @Column(
            name = "place_name",
            length = MeetupDraft.MAX_PLACE_NAME * CrewPost.UTF16_UNITS_PER_CHAR)
    private String placeName;

    @Column(name = "capacity")
    private int capacity;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetupAttendance> attendances = new ArrayList<>();

    protected MeetupPost() {}

    /**
     * 일시가 지금 이전(같음 포함)이면 SCHEDULE_INVALID(409). 장소명·정원 범위는 MeetupDraft 생성자가 먼저 막는다(400, D-155). 정원을
     * 비우면 크루 정원(Crew.CAPACITY). 작성자의 GOING 응답을 만든다(D-151).
     */
    public MeetupPost(long crewId, long authorId, MeetupDraft draft, LocalDateTime now) {
        super(crewId, authorId, new Slot(draft.body(), draft.scheduledAt()), now);
        if (!draft.scheduledAt().isAfter(now)) {
            throw new BusinessRuleException(ReasonCode.SCHEDULE_INVALID);
        }
        this.placeName = draft.placeName();
        this.capacity = draft.capacity() == null ? Crew.CAPACITY : draft.capacity();
        attendances.add(new MeetupAttendance(this, authorId, AttendanceAnswer.GOING, now));
    }

    @Override
    protected int maxBody() {
        return SHORT_BODY;
    }

    @Override
    public PostKind kind() {
        return PostKind.MEETUP;
    }

    /**
     * 참석 응답(FR-145). now ≥ 일정일시면 VOTE_CLOSED, GOING으로 바꾸는데 본인을 뺀 GOING이 이미 정원이면 SCHEDULE_FULL. 있으면
     * 응답 변경, 없으면 MeetupAttendance 추가.
     */
    @Override
    public AttendanceView answer(long memberId, AttendanceAnswer answer, LocalDateTime now) {
        requireNotDeleted();
        if (!now.isBefore(scheduledAt())) {
            throw new BusinessRuleException(ReasonCode.VOTE_CLOSED);
        }
        if (answer == AttendanceAnswer.GOING && othersGoing(memberId) >= capacity) {
            throw new BusinessRuleException(ReasonCode.SCHEDULE_FULL);
        }
        attendanceOf(memberId)
                .ifPresentOrElse(
                        a -> a.change(answer, now),
                        () -> attendances.add(new MeetupAttendance(this, memberId, answer, now)));
        return AttendanceView.of(getId() == null ? 0 : getId(), answer, counts());
    }

    private long othersGoing(long memberId) {
        return attendances.stream()
                .filter(a -> a.answer() == AttendanceAnswer.GOING && a.memberId() != memberId)
                .count();
    }

    @Override
    public AttendanceCounts counts() {
        return AttendanceCounts.of(attendances.stream().map(MeetupAttendance::answer).toList());
    }

    /** 일정일시, 장소명, 정원, 참석·불참·미정 인원, 본인 응답(FR-133). */
    @Override
    public PostSummary summary(long viewerId) {
        return baseSummary(viewerId)
                .withMeetup(
                        new PostSummary.MeetupInfo(
                                scheduledAt(),
                                placeName,
                                capacity,
                                counts(),
                                attendanceOf(viewerId).map(MeetupAttendance::answer).orElse(null)));
    }

    public Optional<MeetupAttendance> attendanceOf(long memberId) {
        return attendances.stream().filter(a -> a.memberId() == memberId).findFirst();
    }

    public List<MeetupAttendance> attendances() {
        return Collections.unmodifiableList(attendances);
    }

    public String placeName() {
        return placeName;
    }

    public int capacity() {
        return capacity;
    }
}
