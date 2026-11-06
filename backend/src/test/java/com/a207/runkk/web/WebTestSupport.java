package com.a207.runkk.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.Tier;
import com.a207.runkk.infra.integration.auth.GoogleLoginGateway;
import com.a207.runkk.infra.integration.auth.KakaoLoginGateway;
import com.a207.runkk.member.Member;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SocialAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * web 계층 MockMvc 시험의 공통 준비(test 프로필: H2 + 내장 Redis). 외부 제공자(카카오·구글)는 가짜로 바꾸고, 가입은 실제 로그인 → 가입 경로로 해
 * 인증 필터까지 함께 시험한다. 같은 설정의 시험 클래스는 스프링 문맥을 함께 쓴다.
 */
@SpringBootTest(classes = RunkkApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class WebTestSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired private MemberRepository memberRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean protected KakaoLoginGateway kakao;
    @MockitoBean protected GoogleLoginGateway google;

    @BeforeEach
    void stubProviders() {
        when(kakao.provider()).thenReturn(ProviderType.KAKAO);
        when(google.provider()).thenReturn(ProviderType.GOOGLE);
        when(kakao.authenticate(anyString()))
                .thenAnswer(inv -> new SocialAccount(ProviderType.KAKAO, inv.getArgument(0)));
    }

    /** 가입한 회원과 그 토큰. */
    protected record Session(long memberId, String accessToken, String refreshToken) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /**
     * 배치고사를 마친 랭크 회원으로 만든다(일반 러닝은 첫 배치를 하지 않으므로 랭크 회원이 필요한 시험이 쓴다, J24-6·D-484). 대결 시험의 주제가 아닌 배치고사
     * 3판을 건너뛴다.
     */
    protected void rank(Session s, Tier tier) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    Member member = memberRepository.findById(s.memberId()).orElseThrow();
                    member.confirmPlacement(tier);
                    memberRepository.save(member);
                });
    }

    /** 새 소셜 계정으로 로그인 → 가입하고 토큰을 돌려준다. */
    protected Session signUp() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String providerToken = "kakao-" + UUID.randomUUID();
        JsonNode login =
                body(
                        mvc.perform(
                                        post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        write(
                                                                Map.of(
                                                                        "provider",
                                                                        "KAKAO",
                                                                        "providerToken",
                                                                        providerToken))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        JsonNode created =
                body(
                        mvc.perform(
                                        post("/api/v1/members")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        write(
                                                                signUpBody(
                                                                        login.get("signUpTicket")
                                                                                .asText(),
                                                                        "웹러너" + n))))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return new Session(
                created.get("memberId").asLong(),
                created.get("accessToken").asText(),
                created.get("refreshToken").asText());
    }

    protected static Map<String, Object> signUpBody(String ticket, String nickname) {
        return Map.of(
                "signUpTicket",
                ticket,
                "nickname",
                nickname,
                "consents",
                Map.of("AGE_14_PLUS", true, "TERMS", true, "PRIVACY", true, "LOCATION", true));
    }

    protected MockHttpServletRequestBuilder as(Session s, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", s.bearer());
    }

    protected String write(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected JsonNode body(String content) throws Exception {
        return json.readTree(content);
    }
}
