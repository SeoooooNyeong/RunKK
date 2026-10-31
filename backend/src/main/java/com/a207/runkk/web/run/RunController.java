package com.a207.runkk.web.run;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.DisposalOrderView;
import com.a207.runkk.running.DisposalService;
import com.a207.runkk.running.HeartRateSample;
import com.a207.runkk.running.LateHeartRateCommand;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.a207.runkk.running.SignalState;
import com.a207.runkk.running.StartDevice;
import com.a207.runkk.running.UnstableSegment;
import com.a207.runkk.running.UploadResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 러닝 기록 업로드·늦은 심박·파기 지시(설계 5.7.1: POST /runs, POST /runs/{id}/heart-rates, GET /me/disposals, POST
 * /me/disposals/{id}/confirm → RunUploadService·DisposalService). 시각은 오프셋이 있는 ISO-8601로 받아 서버
 * 시간대(한국)의 일시로 바꾼다.
 */
@RestController
@RequestMapping("/api/v1")
public class RunController {

    private static final String MEMBER_ID = "memberId";

    /** 1초 간격 12시간. */
    private static final int MAX_POINTS = 43_200;

    private final RunUploadService runUploadService;
    private final DisposalService disposalService;
    private final ZoneId zone;

    public RunController(
            RunUploadService runUploadService, DisposalService disposalService, Clock clock) {
        this.runUploadService = runUploadService;
        this.disposalService = disposalService;
        this.zone = clock.getZone();
    }

    /** 처음이면 STORED, 같은 기록 ID가 이미 있으면 ALREADY_STORED. 둘 다 200이고 앱은 보관함에서 지운다(FR-040). */
    @PostMapping("/runs")
    UploadResponse upload(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody RunUploadRequest request) {
        UploadResult result =
                runUploadService.upload(memberId, new RunUploadCommand(request.toUpload(zone)));
        return new UploadResponse(
                result.recordId(),
                result.alreadyStored() ? "ALREADY_STORED" : "STORED",
                result.valid());
    }

    @PostMapping("/runs/{id}/heart-rates")
    ResponseEntity<Void> appendHeartRates(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable String id,
            @Valid @RequestBody HeartRatesRequest request) {
        runUploadService.appendLateHeartRates(
                memberId,
                new LateHeartRateCommand(
                        id, request.samples().stream().map(s -> s.toSample(zone)).toList()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/disposals")
    List<DisposalOrderView> pendingDisposals(@RequestAttribute(MEMBER_ID) long memberId) {
        return disposalService.pendingFor(memberId);
    }

    @PostMapping("/me/disposals/{id}/confirm")
    ResponseEntity<Void> confirmDisposal(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable long id) {
        disposalService.confirmDevice(memberId, id);
        return ResponseEntity.noContent().build();
    }

    private static LocalDateTime local(OffsetDateTime t, ZoneId zone) {
        return t.atZoneSameInstant(zone).toLocalDateTime();
    }

    /**
     * 완료 러닝 기록(자료사전 완료러닝기록).
     *
     * @param recordId 기기 UUID
     * @param startedAt 시작 일시
     * @param endedAt 종료 일시
     * @param distanceMeters 누적 거리(m)
     * @param elapsedMillis 경과 시간(ms)
     * @param mode 러닝 모드
     * @param cardKind 카드 종류
     * @param battleId 대결 ID
     * @param crewRunPostId 크루런 게시글 ID
     * @param pointCount 선언한 포인트 수
     * @param points 위치 포인트
     * @param unstableSegments 불안정 구간
     * @param heartRates 심박
     * @param pauses 자유 러닝 정지 구간(v0.11 FR-036, D-93), null·빈 목록이면 정지 없음. 규칙 위반은 RunUpload 생성자가
     *     IllegalArgumentException(400)으로 거부한다(F.2.2, D-163)
     * @param startDevice 기록을 시작한 기기(선택, W23, D-378). 없거나 null이면 PHONE(옛 앱), 모르는 값은 400
     */
    record RunUploadRequest(
            @NotBlank @Size(max = 36) String recordId,
            @NotNull OffsetDateTime startedAt,
            @NotNull OffsetDateTime endedAt,
            @PositiveOrZero long distanceMeters,
            @PositiveOrZero long elapsedMillis,
            @NotNull RunMode mode,
            @NotNull CardKind cardKind,
            Long battleId,
            Long crewRunPostId,
            @PositiveOrZero int pointCount,
            @NotNull @Size(max = MAX_POINTS) List<@Valid @NotNull PointRequest> points,
            @Size(max = MAX_POINTS) List<@Valid @NotNull SegmentRequest> unstableSegments,
            @Size(max = MAX_POINTS) List<@Valid @NotNull HeartRateRequest> heartRates,
            @Size(max = RunUpload.MAX_PAUSES) List<@Valid @NotNull PauseRequest> pauses,
            StartDevice startDevice) {

        RunUpload toUpload(ZoneId zone) {
            return new RunUpload(
                    recordId,
                    local(startedAt, zone),
                    local(endedAt, zone),
                    Distance.ofMeters(distanceMeters),
                    Duration.ofMillis(elapsedMillis),
                    mode,
                    cardKind,
                    battleId,
                    crewRunPostId,
                    pointCount,
                    points.stream().map(p -> p.toPoint(zone)).toList(),
                    unstableSegments == null
                            ? List.of()
                            : unstableSegments.stream().map(s -> s.toSegment(zone)).toList(),
                    heartRates == null
                            ? List.of()
                            : heartRates.stream().map(h -> h.toSample(zone)).toList(),
                    pauses == null
                            ? List.of()
                            : pauses.stream().map(p -> p.toInterval(zone)).toList(),
                    startDevice);
        }
    }

    /**
     * 정지 구간(v0.11 FR-036, D-93).
     *
     * @param from 정지 시작 일시
     * @param to 재개 일시
     */
    record PauseRequest(@NotNull OffsetDateTime from, @NotNull OffsetDateTime to) {

        PauseInterval toInterval(ZoneId zone) {
            return new PauseInterval(local(from, zone), local(to, zone));
        }
    }

    /**
     * 위치 포인트.
     *
     * @param measuredAt 측정 일시
     * @param latitude 위도
     * @param longitude 경도
     * @param accuracyMeters 정확도 반경(m)
     * @param signal 신호 상태(없으면 NORMAL)
     */
    record PointRequest(
            @NotNull OffsetDateTime measuredAt,
            double latitude,
            double longitude,
            @PositiveOrZero double accuracyMeters,
            SignalState signal) {

        LocationPoint toPoint(ZoneId zone) {
            return new LocationPoint(
                    local(measuredAt, zone),
                    new GeoPoint(latitude, longitude),
                    accuracyMeters,
                    signal == null ? SignalState.NORMAL : signal);
        }
    }

    /**
     * 불안정 구간.
     *
     * @param from 시작 일시
     * @param to 끝 일시
     * @param distanceMeters 구간 거리(m)
     */
    record SegmentRequest(
            @NotNull OffsetDateTime from,
            @NotNull OffsetDateTime to,
            @PositiveOrZero long distanceMeters) {

        UnstableSegment toSegment(ZoneId zone) {
            return new UnstableSegment(
                    local(from, zone), local(to, zone), Distance.ofMeters(distanceMeters));
        }
    }

    /**
     * 심박 측정 하나.
     *
     * @param measuredAt 측정 일시
     * @param bpm 분당 심박
     */
    record HeartRateRequest(@NotNull OffsetDateTime measuredAt, int bpm) {

        HeartRateSample toSample(ZoneId zone) {
            return new HeartRateSample(local(measuredAt, zone), bpm);
        }
    }

    /**
     * 늦게 온 심박 묶음.
     *
     * @param samples 심박
     */
    record HeartRatesRequest(
            @NotNull @Size(max = MAX_POINTS) List<@Valid @NotNull HeartRateRequest> samples) {}

    /**
     * 업로드 결과.
     *
     * @param recordId 기록 ID
     * @param status STORED 또는 ALREADY_STORED
     * @param valid 유효 러닝인가
     */
    record UploadResponse(String recordId, String status, boolean valid) {}
}
