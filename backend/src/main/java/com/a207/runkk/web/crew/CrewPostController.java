package com.a207.runkk.web.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.BoardDraft;
import com.a207.runkk.crew.BoardPost;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.PhotoContent;
import com.a207.runkk.crew.PhotoUpload;
import com.a207.runkk.crew.PostResult;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 크루 게시판 엔드포인트(설계 5.7.1, 부록 F F.2.1·F.2.2): 목록, 응원·크루런·대면 모임·공지·투표 작성, 게시글(사진 multipart), 삭제, 일정 참석
 * 응답, 공지 고정, 투표 결과·응답, 앨범, 사진. v0.10의 PUT /posts/{id}/vote는 /attendance로 바뀌어 지웠다(F.2.4).
 */
@RestController
@RequestMapping("/api/v1")
public class CrewPostController {

    private static final String MEMBER_ID = "memberId";

    /** 게시 사진 private 캐시 5분(D-100, F.2.2의 1일을 줄임). 신고 숨김·삭제 뒤 기기에 남는 시간을 짧게 한다(제안 값, 구현 결정 목록). */
    static final String PHOTO_CACHE = "private, max-age=300";

    private final CrewPostService crewPostService;
    private final Clock clock;

    CrewPostController(CrewPostService crewPostService, Clock clock) {
        this.crewPostService = crewPostService;
        this.clock = clock;
    }

    /** kinds(쉼표·반복, 옛 단수 kind도 받는다)·pinnedOnly·cursor·size(FR-212). */
    @GetMapping("/crews/{id}/posts")
    CrewDtos.PostListResponse list(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @ModelAttribute CrewDtos.PostListQuery query) {
        return CrewDtos.PostListResponse.of(
                crewPostService.list(memberId, crewId, query.toQuery()), LocalDateTime.now(clock));
    }

    /** CHEER·CREW_RUN·MEETUP·NOTICE·POLL. BOARD는 board-posts(multipart)로 쓰므로 400이다. */
    @PostMapping("/crews/{id}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    CrewDtos.PostResponse write(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @Valid @RequestBody CrewDtos.PostWriteRequest body) {
        PostResult result =
                switch (body.kind()) {
                    case CHEER -> crewPostService.writeCheer(memberId, crewId, body.body());
                    case CREW_RUN ->
                            crewPostService.writeCrewRun(
                                    memberId,
                                    crewId,
                                    body.toCrewRunDraft(LocalDateTime.now(clock)));
                    case MEETUP ->
                            crewPostService.writeMeetup(memberId, crewId, body.toMeetupDraft());
                    case NOTICE ->
                            crewPostService.writeNotice(memberId, crewId, body.toNoticeDraft());
                    case POLL -> crewPostService.writePoll(memberId, crewId, body.toPollDraft());
                    case BOARD ->
                            throw new IllegalArgumentException(
                                    "게시글(BOARD)은 POST /crews/{id}/board-posts로 쓴다");
                };
        return new CrewDtos.PostResponse(result.postId());
    }

    /**
     * 게시글(본문 0~1,000자 + 사진 0~5장, multipart, D-100). 사진마다 PhotoUpload가 크기(1MB, 413)·JPEG 머리(422)를 보고
     * 위치 메타데이터를 지운다(D-142).
     */
    @PostMapping(value = "/crews/{id}/board-posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CrewDtos.PostResponse writeBoard(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam(value = "body", required = false) String body,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        if (photos != null && photos.size() > BoardPost.MAX_PHOTOS) {
            // 사진을 하나씩 검사·변환하기 전에 장수부터 거른다(보안 리뷰 LOW, BoardPost와 같은 PHOTO_LIMIT)
            throw new BusinessRuleException(ReasonCode.PHOTO_LIMIT);
        }
        List<PhotoUpload> uploads =
                photos == null
                        ? List.of()
                        : photos.stream()
                                .filter(file -> !file.isEmpty())
                                .map(CrewPostController::upload)
                                .toList();
        return new CrewDtos.PostResponse(
                crewPostService
                        .writeBoard(memberId, crewId, new BoardDraft(body, uploads))
                        .postId());
    }

    private static PhotoUpload upload(MultipartFile file) {
        try {
            return new PhotoUpload(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        crewPostService.delete(memberId, postId);
    }

    /** 일정 참석 응답(FR-145): 크루런·대면 모임. */
    @PutMapping("/posts/{id}/attendance")
    CrewDtos.AttendanceResponse answerAttendance(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @Valid @RequestBody CrewDtos.AttendanceRequest body) {
        return CrewDtos.AttendanceResponse.of(
                crewPostService.answerAttendance(memberId, postId, body.answer()));
    }

    /** 공지 고정(FR-205, 운영진 이상). */
    @PutMapping("/posts/{id}/pin")
    CrewDtos.PinResponse pin(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @Valid @RequestBody CrewDtos.PinRequest body) {
        return new CrewDtos.PinResponse(
                postId, crewPostService.pin(memberId, postId, body.pinned()));
    }

    /** 투표 결과(FR-207). 응답자는 익명이 아니다. */
    @GetMapping("/posts/{id}/poll")
    CrewDtos.PollResultResponse pollResult(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.PollResultResponse.of(crewPostService.pollResult(memberId, postId));
    }

    /** 투표 응답(FR-207). 마감 전에는 바꿀 수 있다. */
    @PutMapping("/posts/{id}/poll-response")
    CrewDtos.PollResultResponse respondPoll(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @Valid @RequestBody CrewDtos.PollAnswerRequest body) {
        return CrewDtos.PollResultResponse.of(
                crewPostService.respondPoll(memberId, postId, body.optionNo()));
    }

    /** 크루 앨범(FR-212): 보이는 게시글의 사진 최신순, 기본 30장. */
    @GetMapping("/crews/{id}/album")
    CrewDtos.AlbumResponse album(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @ModelAttribute CrewDtos.AlbumQuery query) {
        return CrewDtos.AlbumResponse.of(crewPostService.album(memberId, crewId, query.page()));
    }

    /** 게시 사진 바이트(image/jpeg, FR-212). 사진이 속한 크루의 현재 구성원만, private 캐시 5분(PHOTO_CACHE). */
    @GetMapping("/crew-photos/{photoId}")
    ResponseEntity<byte[]> photo(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("photoId") long photoId) {
        PhotoContent content = crewPostService.photo(memberId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, PHOTO_CACHE)
                .body(content.jpeg());
    }
}
