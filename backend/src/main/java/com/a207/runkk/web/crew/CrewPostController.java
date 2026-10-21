package com.a207.runkk.web.crew;

import com.a207.runkk.common.PageCursor;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.PostKind;
import com.a207.runkk.crew.PostResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 크루 게시판 엔드포인트(설계 5.7.1): 목록, 응원글·모집글 작성, 삭제, 참여 투표. */
@RestController
@RequestMapping("/api/v1")
public class CrewPostController {

    private static final String MEMBER_ID = "memberId";

    private final CrewPostService crewPostService;

    CrewPostController(CrewPostService crewPostService) {
        this.crewPostService = crewPostService;
    }

    @GetMapping("/crews/{id}/posts")
    CrewDtos.PostListResponse list(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam(required = false) PostKind kind,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return CrewDtos.PostListResponse.of(
                crewPostService.list(memberId, crewId, kind, new PageCursor(cursor, size)));
    }

    @PostMapping("/crews/{id}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    CrewDtos.PostResponse write(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @Valid @RequestBody CrewDtos.PostWriteRequest body) {
        PostResult result =
                body.kind() == PostKind.CHEER
                        ? crewPostService.writeCheer(memberId, crewId, body.body())
                        : crewPostService.writeCrewRun(memberId, crewId, body.toDraft());
        return new CrewDtos.PostResponse(result.postId());
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        crewPostService.delete(memberId, postId);
    }

    @PutMapping("/posts/{id}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void vote(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @Valid @RequestBody CrewDtos.VoteRequest body) {
        crewPostService.vote(memberId, postId, body.answer());
    }
}
