package com.a207.runkk.web.admin.celebrity;

import com.a207.runkk.battle.CelebrityInput;
import com.a207.runkk.battle.CelebrityRecordService;
import com.a207.runkk.battle.CelebrityResult;
import com.a207.runkk.battle.RealNameConsent;
import com.a207.runkk.battle.TrackSample;
import com.a207.runkk.common.Distance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유명인 기록 관리(설계 5.7.1 POST·DELETE /admin/celebrities → CelebrityRecordService.register, delete,
 * FR-170~FR-172). 운영자는 web.admin의 OperatorCheckInterceptor가 확인해 요청 속성 actorId로 붙인 계정이다.
 * operator·web.admin을 import하지 않는다(D-50). 삭제도 설계 경로 그대로 /admin/celebrities에 본문(유명인 ID, 삭제 사유)으로
 * 받는다.
 */
@RestController
@RequestMapping("/api/v1/admin/celebrities")
public class CelebrityAdminController {

    private static final String ACTOR_ID = "actorId";
    private static final int NAME_MAX = 40;
    private static final int TEXT_MAX = 500;
    private static final int SPLITS_MAX = 200;
    private static final int FIGURE_KEY_MAX = 20;

    private final CelebrityRecordService celebrityRecordService;

    public CelebrityAdminController(CelebrityRecordService celebrityRecordService) {
        this.celebrityRecordService = celebrityRecordService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CelebrityResult register(
            @RequestAttribute(ACTOR_ID) String actorId,
            @Valid @RequestBody RegisterRequest request) {
        return celebrityRecordService.register(actorId, request.toInput());
    }

    @DeleteMapping
    CelebrityResult delete(
            @RequestAttribute(ACTOR_ID) String actorId, @Valid @RequestBody DeleteRequest request) {
        return celebrityRecordService.delete(actorId, request.celebrityId(), request.reason());
    }

    /**
     * 유명인 기록 입력(자료사전 유명인기록입력). 사진 항목이 없고, 실명은 코치 동의가 있을 때만 이름 칸에 받는다(FR-170, D-240).
     *
     * @param anonymousName 익명 닉네임("마라토너 K")
     * @param distanceMeters 기록 거리(미터)
     * @param finishSeconds 완주 시간(초)
     * @param splits 구간 기록, 없으면 빈 목록
     * @param sourceName 출처명
     * @param sourceUrl 출처 주소, 없으면 null
     * @param realNameConsent 실명 등록 코치 동의(있으면 anonymousName에 실명을 받는다, 반쪽이면 409
     *     REAL_NAME_CONSENT_REQUIRED, v0.14 D-240)
     * @param figureKey 앱 전용 캐릭터 그림 키(영문 소문자·숫자·_·-, "lee"), 없으면 null(공통 셀럽 그림, 2026-10-31 결정)
     */
    record RegisterRequest(
            @Size(max = NAME_MAX) String anonymousName,
            @Positive long distanceMeters,
            @Positive long finishSeconds,
            @Size(max = SPLITS_MAX) List<@Valid @NotNull Split> splits,
            @Size(max = TEXT_MAX) String sourceName,
            @Size(max = TEXT_MAX) @Pattern(regexp = "^https?://[^ ]+$") String sourceUrl,
            @Valid RealNameConsentRequest realNameConsent,
            @Size(max = FIGURE_KEY_MAX) @Pattern(regexp = "^[a-z0-9_-]+$") String figureKey) {

        CelebrityInput toInput() {
            return new CelebrityInput(
                    anonymousName,
                    Distance.ofMeters(distanceMeters),
                    Duration.ofSeconds(finishSeconds),
                    splits == null ? List.of() : splits.stream().map(Split::toSample).toList(),
                    sourceName,
                    sourceUrl,
                    realNameConsent == null ? null : realNameConsent.toConsent(),
                    figureKey);
        }
    }

    /**
     * 실명 등록 동의(FR-170).
     *
     * @param consentedAt 동의 일시
     * @param basis 동의 근거
     */
    record RealNameConsentRequest(LocalDateTime consentedAt, @Size(max = 200) String basis) {

        RealNameConsent toConsent() {
            return new RealNameConsent(consentedAt, basis);
        }
    }

    /**
     * 구간 기록 하나.
     *
     * @param elapsedSeconds 경과 시간(초)
     * @param distanceMeters 누적 거리(미터)
     */
    record Split(@PositiveOrZero long elapsedSeconds, @PositiveOrZero long distanceMeters) {

        TrackSample toSample() {
            return new TrackSample(
                    Duration.ofSeconds(elapsedSeconds), Distance.ofMeters(distanceMeters));
        }
    }

    /**
     * 유명인 기록 삭제 요청(자료사전 유명인기록삭제요청).
     *
     * @param celebrityId 유명인 기록 ID
     * @param reason 삭제 사유
     */
    record DeleteRequest(
            @Positive long celebrityId, @NotBlank @Size(max = TEXT_MAX) String reason) {}
}
