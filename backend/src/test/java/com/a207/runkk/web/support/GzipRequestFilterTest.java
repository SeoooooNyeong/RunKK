package com.a207.runkk.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

/** 요청 압축 해제 필터(v0.14 [임시:D-성-963]): 허용 목록(POST /api/v1/runs)의 gzip 본문만 상한 안에서 푼다. */
@DisplayName("GzipRequestFilter: POST /api/v1/runs의 gzip 본문을 상한 안에서 푼다")
class GzipRequestFilterTest {

    private static final byte[] JSON =
            "{\"recordId\":\"a\",\"n\":1}".getBytes(StandardCharsets.UTF_8);

    private final GzipRequestFilter filter = new GzipRequestFilter(1024);

    @Test
    void inflatesGzipBodyAndHidesContentEncoding() throws Exception {
        MockHttpServletRequest request = runs("gzip", gzip(JSON));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        assertThat(StreamUtils.copyToByteArray(passed.getInputStream())).isEqualTo(JSON);
        assertThat(passed.getHeader("Content-Encoding")).isNull();
        assertThat(Collections.list(passed.getHeaderNames()))
                .noneMatch(name -> name.equalsIgnoreCase("Content-Encoding"));
        assertThat(passed.getContentLength()).isEqualTo(JSON.length);
        assertThat(passed.getContentLengthLong()).isEqualTo(JSON.length);
        assertThat(passed.getHeader("Content-Length")).isEqualTo(Integer.toString(JSON.length));
    }

    @Test
    void readerSeesInflatedText() throws Exception {
        MockHttpServletRequest request = runs("GZIP", gzip(JSON));
        request.setCharacterEncoding("UTF-8");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        String text = ((HttpServletRequest) chain.getRequest()).getReader().readLine();
        assertThat(text).isEqualTo(new String(JSON, StandardCharsets.UTF_8));
    }

    @Test
    void plainRequestsAndOtherPathsPassUntouched() throws Exception {
        MockHttpServletRequest plain = runs(null, JSON);
        MockHttpServletRequest heartRates = runs("gzip", gzip(JSON));
        heartRates.setRequestURI("/api/v1/runs/abc/heart-rates");
        MockHttpServletRequest get = runs("gzip", gzip(JSON));
        get.setMethod("GET");
        MockHttpServletRequest identity = runs("identity", JSON);

        for (MockHttpServletRequest request :
                new MockHttpServletRequest[] {plain, heartRates, get, identity}) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(request.getRequestURI()).isSameAs(request);
        }
    }

    @Test
    void inflatedBodyOverLimitIs413() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(runs("gzip", gzip(new byte[1025])), response, chain);

        assertThat(chain.getRequest()).as("다음 필터로 넘기지 않는다").isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void bodyExactlyAtLimitPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(runs("gzip", gzip(new byte[1024])), new MockHttpServletResponse(), chain);
        assertThat(StreamUtils.copyToByteArray(chain.getRequest().getInputStream())).hasSize(1024);
    }

    @Test
    void brokenGzipIs400() throws Exception {
        byte[] whole = gzip(JSON);
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 6);
        for (byte[] broken : new byte[][] {cut, JSON, new byte[0]}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(runs("gzip", broken), response, chain);
            assertThat(chain.getRequest()).isNull();
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"code\":\"BAD_REQUEST\"");
        }
    }

    @Test
    void defaultLimitIs32MiB() {
        assertThat(GzipRequestFilter.DEFAULT_MAX_INFLATED_BYTES).isEqualTo(32L * 1024 * 1024);
    }

    private static MockHttpServletRequest runs(String encoding, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
        request.setContentType("application/json");
        if (encoding != null) request.addHeader("Content-Encoding", encoding);
        request.setContent(body);
        return request;
    }

    private static byte[] gzip(byte[] plain) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(plain);
        }
        return out.toByteArray();
    }
}
