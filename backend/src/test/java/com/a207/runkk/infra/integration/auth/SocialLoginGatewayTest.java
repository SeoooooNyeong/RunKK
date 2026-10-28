package com.a207.runkk.infra.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.ProviderUnavailableException;
import com.a207.runkk.member.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 카카오·구글 로그인 게이트웨이(C-007). 외부 HTTP는 MockRestServiceServer로 흉내 낸다. */
@org.junit.jupiter.api.DisplayName("카카오·구글 로그인 게이트웨이")
class SocialLoginGatewayTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://provider");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private KakaoLoginGateway kakao(String appId) {
        return new KakaoLoginGateway(builder.build(), appId);
    }

    private GoogleLoginGateway google(String clientIds) {
        return new GoogleLoginGateway(builder.build(), clientIds);
    }

    @Test
    void kakao_validTokenOfOurApp_returnsAccount() {
        server.expect(requestTo("https://provider/v1/user/access_token_info"))
                .andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(
                        withSuccess(
                                "{\"id\":123456,\"expires_in\":3600,\"app_id\":777}",
                                MediaType.APPLICATION_JSON));
        assertThat(kakao("777").authenticate("kakao-token"))
                .isEqualTo(new SocialAccount(ProviderType.KAKAO, "123456"));
        assertThat(kakao("777").provider()).isEqualTo(ProviderType.KAKAO);
        server.verify();
    }

    @Test
    void kakao_otherAppToken_unauthorized_serverError() {
        server.expect(requestTo("https://provider/v1/user/access_token_info"))
                .andRespond(withSuccess("{\"id\":1,\"app_id\":999}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> kakao("777").authenticate("t"))
                .isInstanceOf(AuthFailureException.class);

        server.reset();
        server.expect(requestTo("https://provider/v1/user/access_token_info"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> kakao("777").authenticate("t"))
                .isInstanceOf(AuthFailureException.class);

        server.reset();
        server.expect(requestTo("https://provider/v1/user/access_token_info"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> kakao("777").authenticate("t"))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void kakao_notConfigured_orBlankToken() {
        assertThatThrownBy(() -> kakao("").authenticate("t"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> kakao("777").authenticate(" "))
                .isInstanceOf(AuthFailureException.class);
    }

    @Test
    void google_validIdTokenForOurClient_returnsAccount() {
        server.expect(requestTo("https://provider/tokeninfo?id_token=id-token"))
                .andRespond(
                        withSuccess(
                                "{\"sub\":\"g-1\",\"aud\":\"client-a\",\"iss\":\"https://accounts.google.com\"}",
                                MediaType.APPLICATION_JSON));
        assertThat(google("client-a, client-b").authenticate("id-token"))
                .isEqualTo(new SocialAccount(ProviderType.GOOGLE, "g-1"));
        assertThat(google("client-a").provider()).isEqualTo(ProviderType.GOOGLE);
    }

    @Test
    void google_rejections() {
        server.expect(requestTo("https://provider/tokeninfo?id_token=t"))
                .andRespond(
                        withSuccess(
                                "{\"sub\":\"g-1\",\"aud\":\"someone-else\",\"iss\":\"accounts.google.com\"}",
                                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> google("client-a").authenticate("t"))
                .isInstanceOf(AuthFailureException.class);

        server.reset();
        server.expect(requestTo("https://provider/tokeninfo?id_token=t"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> google("client-a").authenticate("t"))
                .isInstanceOf(AuthFailureException.class);

        server.reset();
        server.expect(requestTo("https://provider/tokeninfo?id_token=t"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> google("client-a").authenticate("t"))
                .isInstanceOf(ProviderUnavailableException.class);

        assertThatThrownBy(() -> google(" ").authenticate("t"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> google("client-a").authenticate(null))
                .isInstanceOf(AuthFailureException.class);
    }
}
