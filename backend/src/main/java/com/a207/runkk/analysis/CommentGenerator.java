package com.a207.runkk.analysis;

import java.util.Optional;

/**
 * 리포트 코멘트를 만든다(설계 6.1.4, 6.3 인터페이스 추출). 구현 AiCommentGenerator, RuleCommentGenerator,
 * FallbackCommentGenerator.
 */
public interface CommentGenerator {

    /** 만들 수 없으면 비어 있다. */
    Optional<Comment> generate(FitnessSummary summary, CommentContext context);
}
