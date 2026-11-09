package com.a207.runkk.web.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewLogoService;
import com.a207.runkk.crew.PhotoUpload;
import com.a207.runkk.web.attempt.AttemptLimiter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 크루 로고 엔드포인트(피그마 F-04, S15P31A207-295, contract-W4 6·7절). 올리기는 multipart 파트 logo 한 장을 게시 사진과 같은
 * 검사(PhotoUpload: 1MB 413, JPEG 422, 위치 메타데이터 제거)로 받고 회원별 1시간 20장까지다(429). 읽기는 로그인 회원 누구나 한다(공개 크루
 * 목록·초대 미리보기에서 크루 밖 사람도 로고를 본다). 로고 ID마다 바이트가 바뀌지 않아 private 캐시 하루를 준다.
 */
@RestController
@RequestMapping("/api/v1")
class CrewLogoController {

    /** 회원별 업로드 상한과 창(쓰이지 않는 로고로 저장소를 채우는 남용 방지). */
    static final int UPLOADS_PER_WINDOW = 20;

    static final Duration UPLOAD_WINDOW = Duration.ofHours(1);

    static final String LOGO_CACHE = "private, max-age=86400";

    private static final String MEMBER_ID = "memberId";

    private final CrewLogoService crewLogoService;
    private final AttemptLimiter uploads;

    CrewLogoController(CrewLogoService crewLogoService, Clock clock) {
        this.crewLogoService = crewLogoService;
        this.uploads = new AttemptLimiter(UPLOADS_PER_WINDOW, UPLOAD_WINDOW, clock);
    }

    /** 검사에 걸린 업로드도 한 번으로 센다(큰 파일을 되풀이해 보내는 것도 막는다). */
    @PostMapping(value = "/crew-logos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CrewDtos.CrewLogoResponse upload(
            @RequestAttribute(MEMBER_ID) long memberId, @RequestPart("logo") MultipartFile logo) {
        if (!uploads.tryAcquire(String.valueOf(memberId))) {
            throw new BusinessRuleException(ReasonCode.TOO_MANY_ATTEMPTS);
        }
        return CrewDtos.CrewLogoResponse.of(crewLogoService.upload(memberId, photoOf(logo)));
    }

    private static PhotoUpload photoOf(MultipartFile file) {
        try {
            return new PhotoUpload(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/crew-logos/{logoId}")
    ResponseEntity<byte[]> logo(@PathVariable("logoId") long logoId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, LOGO_CACHE)
                .header("X-Content-Type-Options", "nosniff") // 보안 리뷰 LOW: 바이트를 다른 형식으로 해석하지 않게
                .body(crewLogoService.load(logoId));
    }
}
