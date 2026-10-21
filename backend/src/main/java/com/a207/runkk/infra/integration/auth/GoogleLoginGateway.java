package com.a207.runkk.infra.integration.auth;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialAccount;
import com.a207.runkk.member.SocialLoginGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글 ID 토큰을 검증해 SocialAccount를 만든다(설계 6.1.13, C-007). GET /tokeninfo?id_token=…으로 서명·만료를 제공자에게
 * 확인하고, aud가 설정한 OAuth 클라이언트 ID이고 iss가 구글일 때만 받는다. 4xx는 인증 실패, 5xx·시간 초과는 제공자 무응답이다.
 */
@Component
public class GoogleLoginGateway implements SocialLoginGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Set<String> ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestClient client;
    private final Set<String> clientIds;

    @Autowired
    public GoogleLoginGateway(
            RestClient.Builder builder,
            @Value("${runkk.auth.google.base-url}") String baseUrl,
            @Value("${runkk.auth.google.client-ids:}") String clientIds) {
        this(builder.requestFactory(timeouts()).baseUrl(baseUrl).build(), clientIds);
    }

    GoogleLoginGateway(RestClient client, String clientIds) {
        this.client = client;
        this.clientIds =
                clientIds == null
                        ? Set.of()
                        : Arrays.stream(clientIds.split(","))
                                .map(String::strip)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public SocialAccount authenticate(String providerToken) {
        if (clientIds.isEmpty()) {
            throw new ProviderUnavailableException("구글 클라이언트 ID 미설정");
        }
        if (providerToken == null || providerToken.isBlank()) {
            throw new AuthFailureException("구글 토큰 없음");
        }
        JsonNode info;
        try {
            info =
                    client.get()
                            .uri(
                                    b ->
                                            b.path("/tokeninfo")
                                                    .queryParam("id_token", providerToken)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new AuthFailureException("구글 인증 실패 status=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ProviderUnavailableException("구글 무응답", e);
        }
        if (info == null || info.path("sub").asText().isEmpty()) {
            throw new AuthFailureException("구글 응답에 계정 ID 없음");
        }
        if (!clientIds.contains(info.path("aud").asText())
                || !ISSUERS.contains(info.path("iss").asText())) {
            throw new AuthFailureException("다른 앱 또는 발급자의 구글 토큰");
        }
        return new SocialAccount(ProviderType.GOOGLE, info.get("sub").asText());
    }

    @Override
    public ProviderType provider() {
        return ProviderType.GOOGLE;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
