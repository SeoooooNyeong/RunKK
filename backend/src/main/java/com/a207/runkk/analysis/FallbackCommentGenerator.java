package com.a207.runkk.analysis;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * primary 결과가 있으면 그것, 없으면 fallback 결과(설계 6.1.4 FallbackCommentGenerator, 6.3 장식). 축소 운영(AI 코멘트 꺼짐)은
 * CommentContext.aiEnabled = false로 primary가 비어 자연히 규칙 문구로 간다. ReportCommentService가
 * CommentGenerator로 받는 기본 빈이다.
 */
@Component
@Primary
public class FallbackCommentGenerator implements CommentGenerator {

    private final CommentGenerator primary;
    private final CommentGenerator fallback;

    public FallbackCommentGenerator(
            @Qualifier("aiCommentGenerator") CommentGenerator primary,
            @Qualifier("ruleCommentGenerator") CommentGenerator fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<Comment> generate(FitnessSummary summary, CommentContext context) {
        Optional<Comment> first = primary.generate(summary, context);
        return first.isPresent() ? first : fallback.generate(summary, context);
    }
}
