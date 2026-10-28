package com.a207.runkk.infra.integration.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.PushResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** FcmPushGateway(D-43): 키가 없으면 로그만, 있으면 서비스 계정 토큰 → v1 발송, 무효 토큰 수집. */
@org.junit.jupiter.api.DisplayName("FCM 푸시 게이트웨이")
class FcmPushGatewayTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-20T06:00:00Z"), ZoneOffset.UTC);
    private static final String SEND = "https://fcm.googleapis.com/v1/projects/runkk/messages:send";
    private static final String TOKEN_URI = "https://oauth2.example/token";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private static ObjectNode credentials() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem =
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder()
                                .encodeToString(
                                        generator.generateKeyPair().getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----\n";
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("client_email", "push@runkk.iam.gserviceaccount.com");
        json.put("private_key", pem);
        json.put("token_uri", TOKEN_URI);
        return json;
    }

    @Test
    void notConfigured_sendsNothing() {
        FcmPushGateway gateway = new FcmPushGateway(builder.build(), "", null, CLOCK);
        assertThat(gateway.isEnabled()).isFalse();
        assertThat(gateway.send(List.of("t"), NotificationType.KICKED, "내용"))
                .isEqualTo(PushResult.none());
        server.verify();
    }

    @Test
    void invalidCredentials_disablesGateway() {
        ObjectNode broken = new ObjectMapper().createObjectNode();
        broken.put("client_email", "a");
        broken.put("private_key", "not-a-key");
        assertThat(new FcmPushGateway(builder.build(), "runkk", broken, CLOCK).isEnabled())
                .isFalse();
    }

    @Test
    void configured_exchangesTokenOnce_sendsEach_collectsInvalid() throws Exception {
        FcmPushGateway gateway = new FcmPushGateway(builder.build(), "runkk", credentials(), CLOCK);
        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"ya29.x\",\"expires_in\":3600}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND))
                .andExpect(header("Authorization", "Bearer ya29.x"))
                .andRespond(withSuccess("{\"name\":\"m1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND))
                .andRespond(
                        withStatus(HttpStatus.NOT_FOUND)
                                .body(
                                        "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}")
                                .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND)).andRespond(withServerError());
        server.expect(requestTo(SEND))
                .andRespond(withSuccess("{\"name\":\"m2\"}", MediaType.APPLICATION_JSON));

        PushResult first =
                gateway.send(List.of("ok", "gone", "flaky"), NotificationType.KICKED, "강제퇴장");
        assertThat(first.sentCount()).isEqualTo(1);
        assertThat(first.invalidTokens()).containsExactly("gone");

        PushResult second = gateway.send(List.of("ok"), NotificationType.KICKED, "다시");
        assertThat(second.sentCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "sendCollapsing: 안드로이드 collapse_key·APNs apns-collapse-id에 묶음 키를 싣는다(D-102)")
    void sendCollapsing_setsCollapseKeys() throws Exception {
        FcmPushGateway gateway = new FcmPushGateway(builder.build(), "runkk", credentials(), CLOCK);
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"ya29.x\",\"expires_in\":3600}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND))
                .andExpect(
                        org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(
                                        "$.message.android.collapse_key")
                                .value("crew_chat_7"))
                .andExpect(
                        org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(
                                        "$.message.apns.headers['apns-collapse-id']")
                                .value("crew_chat_7"))
                .andRespond(withSuccess("{\"name\":\"m1\"}", MediaType.APPLICATION_JSON));

        PushResult result =
                gateway.sendCollapsing(
                        List.of("ok"), NotificationType.CREW_CHAT, "새 메시지", "crew_chat_7");

        assertThat(result.sentCount()).isEqualTo(1);
        server.verify();
    }
}
