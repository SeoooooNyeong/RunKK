package com.a207.runkk.crew.chat;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 탈퇴 때 채팅 자료를 정리한다(설계 6.1.7a, FR-021, D-101). 그 회원이 쓴 메시지는 작성자를 지우고 내용은 남기며(보관 기한 없음, E-v011-6),
 * 그 회원의 리액션은 지운다. crew는 crew.chat을 모르므로 crew.chat이 MemberWithdrawn을 직접 받는다.
 */
@Component
public class ChatWithdrawalListener {

    private final CrewChatMessageRepository messages;
    private final ChatReactionRepository reactions;

    ChatWithdrawalListener(CrewChatMessageRepository messages, ChatReactionRepository reactions) {
        this.messages = messages;
        this.reactions = reactions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        messages.anonymizeAuthor(e.memberId());
        reactions.deleteByMember(e.memberId());
    }
}
