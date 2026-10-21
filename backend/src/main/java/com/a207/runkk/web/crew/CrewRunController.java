package com.a207.runkk.web.crew;

import com.a207.runkk.crew.CrewRunService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 크루런 엔드포인트(설계 5.7.1): 대기실 입장·준비·나가기(D-36으로 나눈 연산 셋), 중간 그만두기, 결과. 진행 중계는 실시간 채널(/ws)로 한다. */
@RestController
@RequestMapping("/api/v1/crew-runs")
public class CrewRunController {

    private static final String MEMBER_ID = "memberId";

    private final CrewRunService crewRunService;

    CrewRunController(CrewRunService crewRunService) {
        this.crewRunService = crewRunService;
    }

    @PostMapping("/{id}/lobby/enter")
    CrewDtos.LobbyResponse enterLobby(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.LobbyResponse.of(crewRunService.enterLobby(memberId, postId), memberId);
    }

    @PostMapping("/{id}/lobby/ready")
    CrewDtos.LobbyResponse toggleReady(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.LobbyResponse.of(crewRunService.toggleReady(memberId, postId), memberId);
    }

    @PostMapping("/{id}/lobby/leave")
    CrewDtos.LobbyResponse leaveLobby(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.LobbyResponse.of(crewRunService.leaveLobby(memberId, postId), memberId);
    }

    @PostMapping("/{id}/quit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void quit(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        crewRunService.quit(memberId, postId);
    }

    @GetMapping("/{id}/result")
    CrewDtos.CrewRunResultResponse result(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.CrewRunResultResponse.of(crewRunService.result(memberId, postId), memberId);
    }
}
