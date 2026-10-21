package com.a207.runkk.analysis;

import com.a207.runkk.policy.OperationPolicy;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM 코멘트(설계 6.1.4 AiCommentGenerator, FR-065, D-58). 동의 && AI 코멘트 켜짐 && 한도 안일 때만 ai 서버를 부른다. 실패·시간
 * 초과·예외면 비어 있다(호출한 Fallback이 규칙 문구로 대체, FR-066). 받은 코멘트의 forRun은 이 클래스가 summary.toRun으로 채운다(ai 서버는
 * 기록 순번을 모른다). 회원 식별자는 한도 계산에만 쓰고 gateway에 넘기지 않는다(NFR-024). 로그에 문구를 남기지 않는다.
 */
@Component
public class AiCommentGenerator implements CommentGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiCommentGenerator.class);

    private final LlmCommentGateway gateway;
    private final LlmCallQuota quota;
    private final OperationPolicy policy;
    private final Duration timeout;

    public AiCommentGenerator(
            LlmCommentGateway gateway,
            LlmCallQuota quota,
            OperationPolicy policy,
            @Value("${runkk.ai.comment-timeout:3s}") Duration timeout) {
        this.gateway = gateway;
        this.quota = quota;
        this.policy = policy;
        this.timeout = timeout;
    }

    @Override
    public Optional<Comment> generate(FitnessSummary summary, CommentContext context) {
        if (!context.llmAgreed() || !context.aiEnabled()) {
            return Optional.empty();
        }
        try {
            if (!quota.tryAcquire(context.memberId(), context.today(), policy.llmCallLimit())) {
                log.info("llm_comment result=quota_exceeded");
                return Optional.empty();
            }
            Optional<Comment> comment =
                    gateway.requestComment(summary, timeout)
                            .map(c -> new Comment(c.text(), CommentSource.AI, summary.toRun()));
            log.info("llm_comment result={}", comment.isPresent() ? "ok" : "failed");
            return comment;
        } catch (RuntimeException e) {
            log.warn("llm_comment result=error error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
