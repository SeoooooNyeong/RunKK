package com.a207.runkk.web.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 본문 압축 해제(v0.14 S15P31A207-327, 설계 5.33). 허용 목록(POST /api/v1/runs)의 요청이 {@code
 * Content-Encoding: gzip}이면 본문을 풀어 다음 필터에 넘긴다. 다음 필터·컨트롤러는 압축을 모른다(Content-Encoding을 감추고
 * Content-Length는 푼 길이).
 *
 * <ul>
 *   <li>푼 바이트가 [maxInflated](기본 32MiB)를 넘으면 그 자리에서 읽기를 멈추고 413 PAYLOAD_TOO_LARGE(압축 폭탄 방지).
 *   <li>gzip 형식이 아니거나 중간에 끊긴 본문은 400 BAD_REQUEST.
 *   <li>무압축 요청과 허용 목록 밖 경로는 건드리지 않는다.
 *   <li>Bearer 인증 머리글이 없는 요청은 풀지 않고 넘긴다(보안 필터가 401을 낸다). 로그인 없이 압축 폭탄으로 힙을 쓰지 못하게 한다.
 *   <li>동시에 푸는 요청은 [concurrency]개(기본 4)까지. 10초 안에 자리가 나지 않으면 503 SERVICE_BUSY(앱은 나중에 다시 보낸다).
 * </ul>
 *
 * <p>보안 필터 체인(SecurityProperties.DEFAULT_FILTER_ORDER)보다 앞에서 돈다. 오류 응답은 공통 오류 형식({@code
 * {"code","message"}}, web.error.ErrorResponse)과 같은 모양이다. 배포 순서: 이 필터가 먼저 운영에 나가고 앱이 그 뒤에 압축해 보낸다.
 */
@Component
public class GzipRequestFilter extends OncePerRequestFilter implements Ordered {

    /** 푼 본문 상한(32MiB). */
    public static final long DEFAULT_MAX_INFLATED_BYTES = 32L * 1024 * 1024;

    /** 보안 필터 체인보다 앞. */
    static final int ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 50;

    /** 압축 본문을 받는 경로(POST만). */
    static final Set<String> PATHS = Set.of("/api/v1/runs");

    private static final Logger log = LoggerFactory.getLogger(GzipRequestFilter.class);
    private static final String GZIP = "gzip";
    private static final String TOO_LARGE_BODY =
            "{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"요청이 너무 커요\"}";
    private static final String BAD_REQUEST_BODY =
            "{\"code\":\"BAD_REQUEST\",\"message\":\"잘못된 요청\"}";
    private static final String BUSY_BODY =
            "{\"code\":\"SERVICE_BUSY\",\"message\":\"잠시 뒤 다시 시도해 주세요\"}";
    private static final int BUFFER = 8192;
    private static final String BEARER = "Bearer ";

    /** 동시에 푸는 요청 수 기본값. 32MiB 상한이면 최악 약 4 × 64MiB(버퍼와 복사본)다. */
    static final int DEFAULT_CONCURRENCY = 4;

    private static final long WAIT_SECONDS = 10;

    private final long maxInflated;
    private final Semaphore permits;

    @Autowired
    public GzipRequestFilter(
            @Value("${runkk.web.gzip-max-inflated-bytes:33554432}") long maxInflated) {
        this(maxInflated, DEFAULT_CONCURRENCY);
    }

    GzipRequestFilter(long maxInflated, int concurrency) {
        if (maxInflated <= 0) throw new IllegalArgumentException("압축 해제 상한 오류");
        if (concurrency < 0) throw new IllegalArgumentException("동시 해제 수 오류");
        this.maxInflated = maxInflated;
        this.permits = new Semaphore(concurrency, true);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) return true;
        String encoding = request.getHeader(HttpHeaders.CONTENT_ENCODING);
        if (encoding == null || !GZIP.equalsIgnoreCase(encoding.trim())) return true;
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.regionMatches(true, 0, BEARER, 0, BEARER.length())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!acquire()) {
            log.info("gzip_request_busy path={}", request.getRequestURI());
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, BUSY_BODY);
            return;
        }
        byte[] inflated;
        try {
            inflated = inflate(request.getInputStream());
        } catch (TooLargeException error) {
            log.info("gzip_request_too_large path={}", request.getRequestURI());
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, TOO_LARGE_BODY);
            return;
        } catch (IOException error) {
            log.info("gzip_request_broken type={}", error.getClass().getSimpleName());
            reject(response, HttpServletResponse.SC_BAD_REQUEST, BAD_REQUEST_BODY);
            return;
        } finally {
            permits.release();
        }
        chain.doFilter(new InflatedRequest(request, inflated), response);
    }

    /** 자리를 얻으면 참. 기다리다 끊기면 interrupt를 되살리고 거짓. */
    private boolean acquire() {
        try {
            return permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 상한을 넘는 순간 읽기를 멈춘다. 형식 오류·끊긴 본문은 IOException(ZipException·EOFException). */
    private byte[] inflate(InputStream body) throws IOException {
        try (GZIPInputStream zip = new GZIPInputStream(body, BUFFER)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER);
            byte[] buffer = new byte[BUFFER];
            long total = 0;
            int read;
            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > maxInflated) throw new TooLargeException();
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void reject(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    /** 상한 초과 신호(형식 오류 IOException과 나누려고 따로 둔다). */
    private static final class TooLargeException extends IOException {
        TooLargeException() {
            super("inflated body too large");
        }
    }

    /** 푼 본문을 내주는 요청. Content-Encoding을 감추고 Content-Length를 푼 길이로 바꾼다. */
    private static final class InflatedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        InflatedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return in.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("비동기 읽기는 지원하지 않는다");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(name)) return null;
            if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return Integer.toString(body.length);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return Collections.enumeration(Set.of(Integer.toString(body.length)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            for (String name : Collections.list(super.getHeaderNames())) {
                if (!HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(name)
                        && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                    names.add(name);
                }
            }
            names.add(HttpHeaders.CONTENT_LENGTH);
            return Collections.enumeration(names);
        }
    }
}
