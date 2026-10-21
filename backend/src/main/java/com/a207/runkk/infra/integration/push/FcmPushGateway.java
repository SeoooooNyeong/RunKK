package com.a207.runkk.infra.integration.push;

import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.PushGateway;
import com.a207.runkk.notification.PushResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * FCM HTTP v1로 푸시를 보낸다(설계 6.1.13, D-43). 받은 토큰으로 보내기만 하고 토큰 표를 갖지 않으며, 제공자가 무효라고 답한 토큰을 PushResult로
 * 돌려준다. 서비스 계정 키(project-id·credentials-file)가 없으면 보내지 않고 운영 로그만 남긴다(FR-175). 토큰·내용은 로그에 남기지 않는다.
 */
@Component
public class FcmPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(FcmPushGateway.class);
    private static final String FCM_BASE = "https://fcm.googleapis.com/v1/projects/";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String TITLE = "런크크";
    private static final Duration ASSERTION_TTL = Duration.ofHours(1);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final RestClient client;
    private final String projectId;
    private final ServiceAccount account;
    private final Clock clock;
    private String accessToken;
    private Instant accessTokenExpiry = Instant.MIN;

    @Autowired
    public FcmPushGateway(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${runkk.push.fcm.project-id:}") String projectId,
            @Value("${runkk.push.fcm.credentials-file:}") String credentialsFile,
            Clock clock) {
        this(
                builder.requestFactory(timeouts()).build(),
                projectId,
                readCredentials(objectMapper, credentialsFile),
                clock);
    }

    FcmPushGateway(RestClient client, String projectId, JsonNode credentials, Clock clock) {
        this.client = client;
        this.projectId = projectId == null ? "" : projectId.strip();
        this.account = ServiceAccount.from(credentials);
        this.clock = clock;
        if (!isEnabled()) {
            log.info("fcm_disabled reason=not_configured mode=log_only");
        }
    }

    @Override
    public PushResult send(List<String> deviceTokens, NotificationType type, String content) {
        if (!isEnabled()) {
            log.info(
                    "push_skipped reason=fcm_not_configured type={} devices={}",
                    type,
                    deviceTokens.size());
            return PushResult.none();
        }
        String bearer = accessToken();
        int sent = 0;
        List<String> invalid = new ArrayList<>();
        for (String token : deviceTokens) {
            try {
                client.post()
                        .uri(FCM_BASE + projectId + "/messages:send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + bearer)
                        .body(message(token, type, content))
                        .retrieve()
                        .toBodilessEntity();
                sent++;
            } catch (HttpClientErrorException e) {
                if (isInvalidToken(e)) {
                    invalid.add(token);
                } else {
                    log.warn("push_rejected type={} status={}", type, e.getStatusCode().value());
                }
            } catch (RestClientException e) {
                log.warn("push_failed type={} error={}", type, e.getClass().getSimpleName());
            }
        }
        return new PushResult(sent, invalid);
    }

    boolean isEnabled() {
        return !projectId.isEmpty() && account != null;
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessTokenExpiry.minus(EXPIRY_MARGIN))) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", account.assertion(now));
        JsonNode response =
                client.post()
                        .uri(account.tokenUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("FCM 접근 토큰 응답 형식 오류");
        }
        this.accessToken = response.get("access_token").asText();
        this.accessTokenExpiry = now.plusSeconds(response.path("expires_in").asLong(3600));
        return accessToken;
    }

    private static Map<String, Object> message(
            String token, NotificationType type, String content) {
        return Map.of(
                "message",
                Map.of(
                        "token", token,
                        "notification", Map.of("title", TITLE, "body", content),
                        "data", Map.of("type", type.name())));
    }

    /** FCM v1: 404 UNREGISTERED, 400 INVALID_ARGUMENT(토큰 형식)는 다시 보내도 실패하는 토큰이다. */
    private static boolean isInvalidToken(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                || body.contains("UNREGISTERED")
                || (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                        && body.contains("INVALID_ARGUMENT"));
    }

    private static JsonNode readCredentials(ObjectMapper objectMapper, String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(Files.readString(Path.of(file)));
        } catch (IOException e) {
            log.warn("fcm_credentials_unreadable mode=log_only");
            return null;
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    /** 서비스 계정 키(client_email, private_key, token_uri). */
    private record ServiceAccount(String clientEmail, PrivateKey privateKey, String tokenUri) {

        static ServiceAccount from(JsonNode json) {
            if (json == null
                    || !json.hasNonNull("client_email")
                    || !json.hasNonNull("private_key")) {
                return null;
            }
            try {
                String pem =
                        json.get("private_key")
                                .asText()
                                .replace("-----BEGIN PRIVATE KEY-----", "")
                                .replace("-----END PRIVATE KEY-----", "")
                                .replaceAll("\\s", "");
                PrivateKey key =
                        KeyFactory.getInstance("RSA")
                                .generatePrivate(
                                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
                return new ServiceAccount(
                        json.get("client_email").asText(),
                        key,
                        json.path("token_uri").asText("https://oauth2.googleapis.com/token"));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.warn("fcm_credentials_invalid mode=log_only");
                return null;
            }
        }

        String assertion(Instant now) {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .issuer(clientEmail)
                            .audience(tokenUri)
                            .claim("scope", SCOPE)
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(ASSERTION_TTL)))
                            .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            try {
                jwt.sign(new RSASSASigner(privateKey));
            } catch (JOSEException e) {
                throw new IllegalStateException("FCM 서비스 계정 서명 실패", e);
            }
            return jwt.serialize();
        }

        @Override
        public String toString() {
            return "ServiceAccount[***]";
        }
    }
}
