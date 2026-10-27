package com.a207.runkk.web.chat;

import com.a207.runkk.crew.chat.ChatCursor;
import com.a207.runkk.crew.chat.ChatDraft;
import com.a207.runkk.crew.chat.ChatPage;
import com.a207.runkk.crew.chat.ChatPostResult;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.crew.chat.ReactionKind;
import com.a207.runkk.crew.chat.ReactionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크루 채팅 REST(설계 5.7.1, 부록 F F.2.2 크루 채팅, D-101): 지난 메시지 읽기, 보내기, 삭제, 리액션. 받기는 실시간 채널(CrewChatPosted
 * 등)이 한다. 요청 DTO → crew.chat 입력, 결과 → 응답 DTO 변환만 하고 업무 규칙이 없다. 보내기는 ChatRateGuard(회원당 초당 5건)를 먼저
 * 거친다(D-132).
 */
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final String MEMBER_ID = "memberId";

    /** 앱이 만든 메시지 ID(UUID 문자열 36자). */
    private static final int CLIENT_MESSAGE_ID_LENGTH = 36;

    private final CrewChatService chatService;
    private final ChatRateGuard rateGuard;

    ChatController(CrewChatService chatService, ChatRateGuard rateGuard) {
        this.chatService = chatService;
        this.rateGuard = rateGuard;
    }

    /** 지난 메시지(before·after·size, 기본 50, 최대 100). before·after를 함께 주면 400. */
    @GetMapping("/crews/{id}/chat/messages")
    ResponseEntity<ChatPageResponse> history(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam(value = "before", required = false) Long before,
            @RequestParam(value = "after", required = false) Long after,
            @RequestParam(value = "size", required = false) Integer size) {
        ChatPage page = chatService.history(memberId, crewId, ChatCursor.of(before, after, size));
        return ResponseEntity.ok(
                new ChatPageResponse(
                        page.messages().stream().map(ChatController::item).toList(),
                        page.hasMore()));
    }

    /** 보내기(201, 같은 clientMessageId 재전송은 200 + 처음 메시지). 회원당 초당 5건을 넘으면 429. */
    @PostMapping("/crews/{id}/chat/messages")
    ResponseEntity<ChatMessageItem> post(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @Valid @RequestBody ChatPostRequest request) {
        rateGuard.admit(memberId);
        ChatPostResult result =
                chatService.post(
                        memberId,
                        crewId,
                        new ChatDraft(request.clientMessageId(), request.content()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(item(result.message()));
    }

    /** 삭제(204). 남의 메시지는 크루장·운영진만(감사 CHAT_FORCE_DELETE). */
    @DeleteMapping("/chat-messages/{id}")
    ResponseEntity<Void> delete(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long messageId) {
        chatService.delete(memberId, messageId);
        return ResponseEntity.noContent().build();
    }

    /** 리액션 달기(멱등). 회원당 초당 10건을 넘으면 429. */
    @PutMapping("/chat-messages/{id}/reactions/{kind}")
    ResponseEntity<ReactionResponse> react(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long messageId,
            @PathVariable("kind") ReactionKind kind) {
        rateGuard.admitReaction(memberId);
        return ResponseEntity.ok(reaction(chatService.react(memberId, messageId, kind)));
    }

    /** 리액션 떼기(멱등). 회원당 초당 10건을 넘으면 429. */
    @DeleteMapping("/chat-messages/{id}/reactions/{kind}")
    ResponseEntity<ReactionResponse> unreact(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long messageId,
            @PathVariable("kind") ReactionKind kind) {
        rateGuard.admitReaction(memberId);
        return ResponseEntity.ok(reaction(chatService.unreact(memberId, messageId, kind)));
    }

    private static ChatMessageItem item(ChatPage.Line line) {
        return new ChatMessageItem(
                line.messageId(),
                line.authorId(),
                line.authorName(),
                line.content(),
                line.sentAt(),
                line.reactions(),
                line.myReactions(),
                line.mine(),
                line.canDelete(),
                line.clientMessageId());
    }

    private static ReactionResponse reaction(ReactionView view) {
        return new ReactionResponse(view.messageId(), view.reactions(), view.myReactions());
    }

    /**
     * 메시지 보내기. 내용은 앞뒤 공백을 지운 뒤 1~500자(코드 포인트, D-143)이고 검사는 crew.chat이 한다(CHAT_EMPTY·BODY_TOO_LONG).
     *
     * @param clientMessageId 앱이 만든 메시지 ID(UUID 36자, 재전송 멱등)
     * @param content 내용
     */
    record ChatPostRequest(
            @NotNull @Size(min = CLIENT_MESSAGE_ID_LENGTH, max = CLIENT_MESSAGE_ID_LENGTH)
                    String clientMessageId,
            @NotNull String content) {}

    /**
     * 메시지 페이지. 목록은 항상 오래된 → 새 순이다(FR-209).
     *
     * @param messages 메시지
     * @param hasMore 이 페이지 밖에 더 있음
     */
    record ChatPageResponse(List<ChatMessageItem> messages, boolean hasMore) {}

    /**
     * 메시지 한 줄.
     *
     * @param messageId 메시지 ID
     * @param authorId 작성자 회원 ID, 탈퇴면 null
     * @param authorName 작성자 표시명(탈퇴면 "탈퇴한 사용자")
     * @param content 내용
     * @param sentAt 보낸 일시
     * @param reactions 리액션 종류별 수
     * @param myReactions 본인이 단 리액션
     * @param mine 본인 메시지
     * @param canDelete 지울 수 있음(본인 또는 운영진 이상)
     * @param clientMessageId 앱 메시지 ID(본인 메시지만)
     */
    record ChatMessageItem(
            long messageId,
            Long authorId,
            String authorName,
            String content,
            OffsetDateTime sentAt,
            Map<ReactionKind, Integer> reactions,
            Set<ReactionKind> myReactions,
            boolean mine,
            boolean canDelete,
            String clientMessageId) {}

    /**
     * 리액션 결과.
     *
     * @param messageId 메시지 ID
     * @param reactions 리액션 종류별 수
     * @param myReactions 본인이 단 리액션
     */
    record ReactionResponse(
            long messageId, Map<ReactionKind, Integer> reactions, Set<ReactionKind> myReactions) {}
}
