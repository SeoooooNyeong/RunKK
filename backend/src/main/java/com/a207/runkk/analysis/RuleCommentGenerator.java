package com.a207.runkk.analysis;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** 규칙 기반 문구(설계 6.1.4 RuleCommentGenerator, FR-066). 언제나 값을 돌려준다(출처 RULE). */
@Component
public class RuleCommentGenerator implements CommentGenerator {

    private final RuleCommentCatalog catalog;

    public RuleCommentGenerator(RuleCommentCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<Comment> generate(FitnessSummary summary, CommentContext context) {
        return Optional.of(new Comment(catalog.pick(summary), CommentSource.RULE, summary.toRun()));
    }
}
