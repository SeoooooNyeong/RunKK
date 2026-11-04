package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.TokenIssuer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 14장 T-17 셀카 비저장(D-59, FR-156): 실제 HTTP multipart 업로드부터 작업 접수까지 api의 multipart 임시 디렉터리에 파일이
 * 0개이고(1MB 이하는 메모리로만 받는다), 같은 회원의 동시 요청은 하나만 접수하며(DB 예약), 1MB를 넘으면 413이다. ai 서버는 가짜 게이트웨이로 바꾼다(ai
 * 컨테이너 쪽 비저장은 ai 저장소 시험이 맡는다).
 */
@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("T-17 셀카 비저장·비동기 작업 경합")
class SelfieNoStorageIntegrationTest {

    private static final long WAIT_SECONDS = 10;
    private static final int ONE_AND_HALF_MB = 1_536 * 1_024;
    private static Path uploadDir;

    @DynamicPropertySource
    static void multipartLocation(DynamicPropertyRegistry registry) throws IOException {
        uploadDir = Files.createTempDirectory("runkk-multipart");
        registry.add("spring.servlet.multipart.location", uploadDir::toString);
    }

    @MockitoBean com.a207.runkk.avatar.FaceJobGateway faceJobGateway;
    @Autowired TestRestTemplate http;
    @Autowired MemberRepository members;
    @Autowired MemberSettingService memberSettings;
    @Autowired TokenIssuer tokens;
    @Autowired TransactionTemplate tx;
    @Autowired com.a207.runkk.avatar.AvatarRepository avatars;

    private static byte[] jpeg(int size) {
        byte[] image = new byte[size];
        image[0] = (byte) 0xFF;
        image[1] = (byte) 0xD8;
        image[2] = (byte) 0xFF;
        return image;
    }

    private static long filesIn(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<String> upload(String token, byte[] image) {
        HttpHeaders part = new HttpHeaders();
        part.setContentType(MediaType.IMAGE_JPEG);
        ByteArrayResource file =
                new ByteArrayResource(image) {
                    @Override
                    public String getFilename() {
                        return "selfie.jpg";
                    }
                };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("selfie", new HttpEntity<>(file, part));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return http.exchange(
                "/api/v1/avatar/selfie",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private String consentedMember(String nickname) {
        long id = tx.execute(s -> members.save(MemberFixtures.member(nickname)).getId());
        memberSettings.giveConsent(id, ConsentItem.FACE_TRANSFER);
        return tokens.issue(id).accessToken();
    }

    @Test
    @DisplayName("변환 중 같은 회원의 두 번째 요청은 409 SELFIE_IN_PROGRESS, 디스크 임시 파일 0개, ai 호출 1번")
    void concurrentSelfies_onlyOneConverts_andNothingOnDisk() throws Exception {
        String token = consentedMember("셀카곰");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong filesDuringConversion = new AtomicLong(-1);
        when(faceJobGateway.submit(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            filesDuringConversion.set(filesIn(uploadDir));
                            entered.countDown();
                            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                            return new com.a207.runkk.avatar.FaceJob(
                                    "test-job",
                                    com.a207.runkk.avatar.FaceJob.Status.QUEUED,
                                    null,
                                    null);
                        });

        CompletableFuture<ResponseEntity<String>> first =
                CompletableFuture.supplyAsync(() -> upload(token, jpeg(900 * 1_024)));
        assertThat(entered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        ResponseEntity<String> second = upload(token, jpeg(900 * 1_024));
        release.countDown();
        ResponseEntity<String> done = first.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("SELFIE_IN_PROGRESS");
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(done.getBody()).contains("\"status\":\"QUEUED\"");
        verify(faceJobGateway, times(1)).submit(org.mockito.ArgumentMatchers.anyLong(), any());
        assertThat(filesDuringConversion.get()).as("변환 중 multipart 임시 파일").isZero();
        assertThat(filesIn(uploadDir)).as("요청 뒤 multipart 임시 파일").isZero();
    }

    @Test
    void concurrentResultsSpendQuotaOnce() throws Exception {
        String token = consentedMember("완료중복곰");
        AtomicLong owner = new AtomicLong();
        when(faceJobGateway.submit(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            owner.set(inv.getArgument(0));
                            return new com.a207.runkk.avatar.FaceJob(
                                    "result-job",
                                    com.a207.runkk.avatar.FaceJob.Status.QUEUED,
                                    null,
                                    null);
                        });
        assertThat(upload(token, jpeg(32)).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        CountDownLatch resultCalls = new CountDownLatch(2);
        when(faceJobGateway.result(owner.get(), "result-job"))
                .thenAnswer(
                        inv -> {
                            resultCalls.countDown();
                            assertThat(resultCalls.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                            return new com.a207.runkk.avatar.GeneratedHead(
                                    "result-head",
                                    Long.toString(owner.get()),
                                    "face",
                                    "eyes",
                                    "hair",
                                    Map.of(),
                                    "short",
                                    Map.of(),
                                    Map.of(),
                                    "now");
                        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        java.util.function.Supplier<ResponseEntity<String>> getResult =
                () ->
                        http.exchange(
                                "/api/v1/avatar/selfie/jobs/result-job/result",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class);
        CompletableFuture<ResponseEntity<String>> first = CompletableFuture.supplyAsync(getResult);
        CompletableFuture<ResponseEntity<String>> second = CompletableFuture.supplyAsync(getResult);
        assertThat(first.get(WAIT_SECONDS, TimeUnit.SECONDS).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(second.get(WAIT_SECONDS, TimeUnit.SECONDS).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Integer count = tx.execute(s -> avatars.findOrCreate(owner.get()).creationCount());
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("1MB를 넘는 셀카는 413이고 ai를 부르지 않는다")
    void oversizedSelfie_isRejected() {
        String token = consentedMember("큰사진곰");
        ResponseEntity<String> response = upload(token, jpeg(ONE_AND_HALF_MB));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verify(faceJobGateway, times(0)).submit(org.mockito.ArgumentMatchers.anyLong(), any());
        assertThat(filesIn(uploadDir)).isZero();
    }
}
