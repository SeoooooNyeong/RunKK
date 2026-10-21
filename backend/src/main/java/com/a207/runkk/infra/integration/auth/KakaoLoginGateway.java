package com.a207.runkk.infra.integration.auth;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.member.SocialLoginGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 접근 토큰을 검증해 SocialAccount를 만든다(설계 6.1.13, C-007). GET /v1/user/access_token_info로 토큰의 회원 ID와
 * 발급 앱 ID를 받고, 앱 ID가 설정 값과 다르면 다른 앱의 토큰으로 보고 거부한다. 4xx는 인증 실패, 5xx·시간 초과는 제공자 무응답이다.
 */
@Component
public class KakaoLoginGateway implements SocialLoginGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient client;
    private final String appId;

    @Autowired
    public KakaoLoginGateway(
            RestClient.Builder builder,
            @Value("${runkk.auth.kakao.base-url}") String baseUrl,
            @Value("${runkk.auth.kakao.app-id:}") String appId) {
        this(builder.requestFactory(timeouts()).baseUrl(baseUrl).build(), appId);
    }

    KakaoLoginGateway(RestClient client, String appId) {
        this.client = client;
        this.appId = appId == null ? "" : appId.strip();
    }

    @Override
    public SocialAccount authenticate(String providerToken) {
        if (appId.isEmpty()) {
            throw new ProviderUnavailableException("카카오 앱 ID 미설정");
        }
        if (providerToken == null || providerToken.isBlank()) {
            throw new AuthFailureException("카카오 토큰 없음");
        }
        JsonNode info;
        try {
            info =
                    client.get()
                            .uri("/v1/user/access_token_info")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                            .retrieve()
                            .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new AuthFailureException("카카오 인증 실패 status=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ProviderUnavailableException("카카오 무응답", e);
        }
        if (info == null || !info.hasNonNull("id")) {
            throw new AuthFailureException("카카오 응답에 회원 ID 없음");
        }
        if (!appId.equals(info.path("app_id").asText())) {
            throw new AuthFailureException("다른 앱의 카카오 토큰");
        }
        return new SocialAccount(ProviderType.KAKAO, info.get("id").asText());
    }

    @Override
    public ProviderType provider() {
        return ProviderType.KAKAO;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
