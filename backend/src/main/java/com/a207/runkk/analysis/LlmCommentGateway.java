package com.a207.runkk.analysis;

import java.time.Duration;
import java.util.Optional;

/**
 * ai 서버 코멘트 포트(설계 6.1.4, D-58). 회원 식별자 매개변수가 없다(NFR-024). 구현
 * infra.integration.ai.AiServerCommentGateway. 200이 아니거나 시간 초과·파싱 오류면 비어 있다.
 */
public interface LlmCommentGateway {

    Optional<Comment> requestComment(FitnessSummary summary, Duration timeout);
}
