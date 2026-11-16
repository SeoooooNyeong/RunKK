package com.a207.runkk.web.crew;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.crew.CrewRunResultView;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.LobbyView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크루런 엔드포인트(설계 5.7.1): 대기실 입장·준비·나가기(D-36으로 나눈 연산 셋), 일시정지, 내 크루런 끝내기, 결과(합산 방식, S15P31A207-236).
 * 설계 05-32(크루런 실시간 반영): 안전한 대기실 읽기(GET /lobby), 멱등 준비(PUT /lobby/ready), 모집자 지금 시작(POST /start), 중간
 * 참여(POST /join). 기존 토글 POST /lobby/ready와 입장 POST /lobby/enter는 옛 앱 호환으로 그대로 둔다. 진행 중계는 실시간
 * 채널(/ws)로 한다. 대기실·결과 참가자의 아바타 얼굴 키는 AvatarQuery.faceKeysOf로 붙인다(크루원 아바타, S15P31A207-236. 크루원 목록의
 * Runimal 유형처럼 표현 계층이 두 조회 결과를 붙인다, D-46). 중계 메시지에는 싣지 않고 앱이 대기실 응답의 memberId로 맞춘다.
 */
@RestController
@RequestMapping("/api/v1/crew-runs")
public class CrewRunController {

    private static final String MEMBER_ID = "memberId";

    private final CrewRunService crewRunService;
    private final AvatarQuery avatarQuery;

    CrewRunController(CrewRunService crewRunService, AvatarQuery avatarQuery) {
        this.crewRunService = crewRunService;
        this.avatarQuery = avatarQuery;
    }

    /** 안전한 대기실 읽기(05-32 [임시:D-성-879]): 준비·참가·순번을 바꾸지 않는다. 출발·종료 뒤에도 현황을 준다. */
    @GetMapping("/{id}/lobby")
    CrewDtos.LobbyResponse readLobby(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return lobby(crewRunService.readLobby(memberId, postId), memberId);
    }

    /** 멱등 준비(05-32): 본문 {"ready": true|false}. 같은 값을 다시 보내도 같다. 카운트다운 중 false는 출발 후보에서만 뺀다. */
    @PutMapping("/{id}/lobby/ready")
    CrewDtos.LobbyResponse setReady(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @Valid @RequestBody CrewDtos.ReadyRequest body) {
        return lobby(crewRunService.setReady(memberId, postId, body.ready()), memberId);
    }

    /** 모집자 지금 시작(05-32): 최초 예약만 준비자 2명 이상, 재요청은 같은 예약(countdownToken·startAt)을 돌려준다. */
    @PostMapping("/{id}/start")
    CrewDtos.LobbyResponse startNow(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return lobby(crewRunService.startNow(memberId, postId), memberId);
    }

    /** 중간 참여(05-32): 출발 뒤 종료 전 현재 크루원. 재요청은 같은 joinedAt·measurementRecordId. */
    @PostMapping("/{id}/join")
    CrewDtos.ParticipationResponse join(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.ParticipationResponse.of(crewRunService.join(memberId, postId), memberId);
    }

    @PostMapping("/{id}/lobby/enter")
    CrewDtos.LobbyResponse enterLobby(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return lobby(crewRunService.enterLobby(memberId, postId), memberId);
    }

    @PostMapping("/{id}/lobby/ready")
    CrewDtos.LobbyResponse toggleReady(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return lobby(crewRunService.toggleReady(memberId, postId), memberId);
    }

    @PostMapping("/{id}/lobby/leave")
    CrewDtos.LobbyResponse leaveLobby(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return lobby(crewRunService.leaveLobby(memberId, postId), memberId);
    }

    /** 일시정지(합산 방식). 본문 {elapsedSeconds}는 생략할 수 있다. 재개는 진행값으로 판단해 따로 없다. */
    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pause(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @RequestBody(required = false) CrewDtos.PauseRequest body) {
        crewRunService.pause(memberId, postId, body == null ? null : body.elapsed());
    }

    /** 내 크루런 끝내기(합산 방식). 본문 {distanceMeters, elapsedSeconds}는 생략할 수 있다. */
    @PostMapping("/{id}/quit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void quit(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @RequestBody(required = false) CrewDtos.QuitRequest body) {
        crewRunService.quit(memberId, postId, body == null ? null : body.toFinal());
    }

    @GetMapping("/{id}/result")
    CrewDtos.CrewRunResultResponse result(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        CrewRunResultView view = crewRunService.result(memberId, postId);
        Map<Long, String> faces =
                avatarQuery.faceKeysOf(
                        view.participants().stream()
                                .map(CrewRunResultView.Line::memberId)
                                .filter(Objects::nonNull)
                                .toList());
        return CrewDtos.CrewRunResultResponse.of(view, memberId, faces);
    }

    private CrewDtos.LobbyResponse lobby(LobbyView view, long viewerId) {
        List<Long> ids = view.participants().stream().map(LobbyView.Entry::memberId).toList();
        return CrewDtos.LobbyResponse.of(
                view, viewerId, avatarQuery.faceKeysOf(ids), avatarQuery.looksOf(ids));
    }
}
