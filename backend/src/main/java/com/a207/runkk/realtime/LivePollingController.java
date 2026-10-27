package com.a207.runkk.realtime;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 폴링 대체 방식의 REST 입구(설계 6.1.13, 5.7.1, C-004). runkk.live=polling일 때만 연다. 메시지 본문은 WebSocket과 같은
 * JSON이다. 회원 ID는 web.auth의 AuthFilter가 붙인 요청 속성이다.
 */
@RestController
@RequestMapping("/api/v1/live")
@ConditionalOnProperty(name = "runkk.live", havingValue = "polling")
public class LivePollingController {

    private static final String MEMBER_ID = "memberId";

    private final PollingMessenger pollingMessenger;
    private final RealtimeMessageSerializer serializer;
    private final ProgressDispatcher dispatcher;

    LivePollingController(
            PollingMessenger pollingMessenger,
            RealtimeMessageSerializer serializer,
            ProgressDispatcher dispatcher) {
        this.pollingMessenger = pollingMessenger;
        this.serializer = serializer;
        this.dispatcher = dispatcher;
    }

    /** 그동안 쌓인 서버 → 앱 메시지 JSON 배열(PollingMessenger.drain). */
    @GetMapping(value = "/poll", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> poll(@RequestAttribute(MEMBER_ID) long memberId) {
        return ResponseEntity.ok("[" + String.join(",", pollingMessenger.drain(memberId)) + "]");
    }

    /**
     * 앱 → 서버 진행값(InboundProgress JSON). WebSocket과 같은 1024바이트 상한을 넘거나 형식이 틀리면 400(코드 리뷰 2차 L22).
     */
    @PostMapping(value = "/progress", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> progress(
            @RequestAttribute(MEMBER_ID) long memberId, @RequestBody String body) {
        if (body.getBytes(StandardCharsets.UTF_8).length > LiveSocketHandler.MAX_MESSAGE_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        return serializer
                .readInbound(body)
                .filter(InboundProgress.class::isInstance)
                .map(
                        inbound -> {
                            dispatcher.dispatch(memberId, (InboundProgress) inbound);
                            return ResponseEntity.noContent().<Void>build();
                        })
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }
}
