package com.a207.runkk.infra.integration.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ai 서버 연결을 만든다(설계 6.1.13, D-58). 모든 요청에 X-Internal-Token을 붙이고, 연결 시간 제한 0.3초, 읽기 시간 제한은
 * 요청별(comment 3초, face 15초)이다. /comments와 /faces는 HttpClient(연결 풀)를 나눠 얼굴 변환이 느려도 코멘트가 막히지 않는다.
 * 재시도하지 않는다. 코멘트 읽기 시간 제한은 ai 라우터 전체 제한 2.7초보다 커야 하므로 시작 때 확인하고 아니면 기동을 멈춘다(design-review.md H-22).
 */
@Component
public class AiServerClient {

    /** 내부 토큰 헤더(D-58, ai verify_internal_token). */
    static final String TOKEN_HEADER = "X-Internal-Token";

    /** 연결 시간 제한(D-58). */
    static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);

    /** ai CommentRouter의 asyncio.timeout(D-58). 코멘트 읽기 시간 제한은 이보다 커야 한다. */
    static final Duration AI_COMMENT_ROUTER_LIMIT = Duration.ofMillis(2_700);

    private final RestClient comments;
    private final RestClient faces;
    private final RestClient privacy;

    @Autowired
    AiServerClient(Server server) {
        this(
                server.serverUrl(),
                server.internalToken(),
                server.commentTimeout(),
                server.faceTimeout());
    }

    AiServerClient(
            String serverUrl, String internalToken, Duration commentTimeout, Duration faceTimeout) {
        if (commentTimeout.compareTo(AI_COMMENT_ROUTER_LIMIT) <= 0) {
            throw new IllegalStateException(
                    "runkk.ai.comment-timeout은 ai 라우터 제한 2.7초보다 커야 한다: " + commentTimeout);
        }
        this.comments = client(serverUrl, internalToken, commentTimeout);
        this.faces = client(serverUrl, internalToken, faceTimeout);
        this.privacy = client(serverUrl, internalToken, Duration.ofSeconds(15));
    }

    /** 시험용: 이미 만든 클라이언트를 쓴다(MockRestServiceServer). */
    AiServerClient(RestClient comments, RestClient faces) {
        this.comments = comments;
        this.faces = faces;
        this.privacy = comments;
    }

    private static RestClient client(String serverUrl, String token, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(serverUrl)
                .defaultHeader(TOKEN_HEADER, token)
                .requestFactory(factory)
                .build();
    }

    /** /comments 전용 연결(AiServerCommentGateway). */
    RestClient comments() {
        return comments;
    }

    /** 비동기 얼굴 작업 접수·조회 전용 연결(AiServerFaceJobGateway). */
    RestClient faces() {
        return faces;
    }

    /** AI 파기 전체 제한 10초보다 긴 전용 읽기 제한 15초. */
    RestClient privacy() {
        return privacy;
    }

    /**
     * ai 서버 연결(runkk.ai.*, D-58, 코드 리뷰 2차 L1).
     *
     * @param serverUrl runkk.ai.server-url
     * @param internalToken runkk.ai.internal-token(X-Internal-Token)
     * @param commentTimeout runkk.ai.comment-timeout(ai 라우터 제한 2.7초보다 커야 한다)
     * @param faceTimeout runkk.ai.face-timeout
     */
    @ConfigurationProperties("runkk.ai")
    public record Server(
            String serverUrl, String internalToken, Duration commentTimeout, Duration faceTimeout) {

        /** 내부 토큰을 로그·오류 메시지에 남기지 않는다. */
        @Override
        public String toString() {
            return "Server[serverUrl=" + serverUrl + ", internalToken=***]";
        }
    }
}
