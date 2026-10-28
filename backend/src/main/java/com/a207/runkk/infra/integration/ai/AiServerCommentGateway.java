package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.analysis.Comment;
import com.a207.runkk.analysis.CommentSource;
import com.a207.runkk.analysis.FitnessSummary;
import com.a207.runkk.analysis.LlmCommentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * ai 서버 POST /comments를 부른다(설계 6.1.13, D-58, NFR-024). FitnessSummary를 SummaryIn 항목(averageScore,
 * distanceChangeMeters, paceChangeSeconds, frequentDay, frequentHourBand, runCount)만 JSON으로 보낸다. 기록
 * 순번(fromRun·toRun)과 회원 식별자는 보내지 않는다(ai는 정의 밖 항목을 422로 거부한다). 200이면 CommentOut.text로
 * Comment(forRun은 호출한 AiCommentGenerator가 채운다), 200이 아니거나(401·422·503·504) 시간 초과·파싱 오류·빈 문구면 비어 있다.
 * 읽기 시간 제한은 AiServerClient의 /comments 전용 연결(runkk.ai.comment-timeout, 기동 때 2.7초 초과 확인)이 지키고,
 * timeout 인자는 같은 설정 값이라 로그에만 쓴다. 요청·응답 본문을 로그에 남기지 않는다.
 */
@Component
public class AiServerCommentGateway implements LlmCommentGateway {

    static final String PATH = "/comments";

    /** ai CommentOut.text 상한(D-58 출력 검증 120자). */
    static final int MAX_TEXT = 120;

    private static final Logger log = LoggerFactory.getLogger(AiServerCommentGateway.class);

    private final AiServerClient client;
    private final ObjectMapper objectMapper;

    public AiServerCommentGateway(AiServerClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Comment> requestComment(FitnessSummary summary, Duration timeout) {
        long started = System.nanoTime();
        try {
            Optional<Comment> comment =
                    client.comments()
                            .post()
                            .uri(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(SummaryIn.of(summary))
                            .exchange(
                                    (request, response) ->
                                            translate(
                                                    response.getStatusCode().value(),
                                                    response.getBody(),
                                                    summary.toRun()),
                                    true);
            log.info(
                    "ai_comment result={} latencyMs={}",
                    comment.isPresent() ? "ok" : "rejected",
                    elapsedMillis(started));
            return comment;
        } catch (RestClientException e) {
            log.warn(
                    "ai_comment result=error error={} latencyMs={} limit={}",
                    e.getClass().getSimpleName(),
                    elapsedMillis(started),
                    timeout);
            return Optional.empty();
        }
    }

    private Optional<Comment> translate(int status, InputStream body, int forRun) {
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            log.error("ai_comment_unauthorized status=401 hint=RUNKK_INTERNAL_TOKEN 불일치");
            return Optional.empty();
        }
        if (status != HttpStatus.OK.value()) {
            log.info("ai_comment_rejected status={}", status);
            return Optional.empty();
        }
        String text = readText(body);
        if (text.isBlank() || text.length() > MAX_TEXT) {
            log.warn("ai_comment_invalid_text length={}", text.length());
            return Optional.empty();
        }
        return Optional.of(new Comment(text, CommentSource.AI, forRun));
    }

    private String readText(InputStream body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return json == null ? "" : json.path("text").asText("").strip();
        } catch (IOException e) {
            return "";
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    /**
     * ai SummaryIn(extra = forbid). 회원 식별자·기록 순번을 담지 않는다.
     *
     * @param averageScore 평균 러닝 점수
     * @param distanceChangeMeters 거리 변화(미터)
     * @param paceChangeSeconds 페이스 변화(km당 초)
     * @param frequentDay 자주 달린 요일(MONDAY~SUNDAY)
     * @param frequentHourBand 자주 달린 시간대(0~7)
     * @param runCount 요약에 쓴 최근 러닝 수(1~10)
     */
    record SummaryIn(
            double averageScore,
            long distanceChangeMeters,
            int paceChangeSeconds,
            String frequentDay,
            int frequentHourBand,
            int runCount) {

        static SummaryIn of(FitnessSummary s) {
            return new SummaryIn(
                    s.averageScoreValue(),
                    s.distanceChangeMeters(),
                    s.paceChangeSeconds(),
                    s.frequentDay().name(),
                    s.frequentHourBand(),
                    s.runCount());
        }
    }
}
