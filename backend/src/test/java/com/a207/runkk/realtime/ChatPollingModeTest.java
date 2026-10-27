package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.chat.ChatCursor;
import com.a207.runkk.crew.chat.ChatDraft;
import com.a207.runkk.crew.chat.ChatRoomPresence;
import com.a207.runkk.crew.chat.CrewChatMessenger;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 폴링 대체 모드(runkk.live=polling, F.3.2, C-004)로 기동하면 채팅 포트가 PollingChatPresence·PollingMessenger로
 * 이어지고, 보낸 메시지는 우편함에 들어가지 않으며 작성자를 뺀 크루원 모두가 CREW_CHAT 알림을 받는다. 앱 채팅방은 ?after=로 읽는다. TC-6622.
 */
@SpringBootTest(classes = RunkkApplication.class, properties = "runkk.live=polling")
@ActiveProfiles("test")
@org.junit.jupiter.api.DisplayName("폴링 대체 모드의 채팅")
class ChatPollingModeTest {

    @Autowired ChatRoomPresence presence;
    @Autowired CrewChatMessenger messenger;
    @Autowired PollingMessenger polling;
    @Autowired CrewChatService chat;
    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private long member() {
        String name = "폴링" + UUID.randomUUID().toString().substring(0, 6);
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    private int chatRows(long crewId) {
        return jdbc.queryForObject(
                "select count(*) from notification where type = 'CREW_CHAT' and target_id = ?",
                Integer.class,
                String.valueOf(crewId));
    }

    private void awaitChatRows(long crewId, int expected) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (chatRows(crewId) < expected && System.currentTimeMillis() < deadline) {
            LockSupport.parkNanos(20_000_000L);
        }
        assertThat(chatRows(crewId)).isEqualTo(expected);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6622 방 입장 빈 집합, 전송 0(우편함 0), 작성자를 뺀 크루원 모두에게 CREW_CHAT, ?after=로 읽힌다")
    void pollingMode() {
        assertThat(presence).isInstanceOf(PollingChatPresence.class);
        assertThat(messenger).isSameAs(polling);
        long leader = member();
        long b = member();
        long c = member();
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "폴링크루" + UUID.randomUUID().toString().substring(0, 6),
                                        "함께",
                                        Set.of(PersonalityTag.SOCIAL_RUN),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        membership.apply(b, JoinTarget.byCrew(crewId));
        membership.apply(c, JoinTarget.byCrew(crewId));
        long last =
                chat.post(leader, crewId, new ChatDraft(UUID.randomUUID().toString(), "먼저"))
                        .message()
                        .messageId();
        awaitChatRows(crewId, 2); // 방 밖 알림은 실행기에서 저장된다

        chat.post(b, crewId, new ChatDraft(UUID.randomUUID().toString(), "폴링으로 보냄"));

        assertThat(presence.presentMembers(crewId)).isEmpty();
        for (long m : List.of(leader, b, c)) {
            assertThat(polling.drain(m)).isEmpty();
        }
        awaitChatRows(crewId, 3);
        assertThat(
                        jdbc.queryForList(
                                "select recipient_id from notification where type = 'CREW_CHAT'"
                                        + " and target_id = ?",
                                Long.class,
                                String.valueOf(crewId)))
                .containsExactlyInAnyOrder(b, c, leader); // c는 두 메시지가 한 행으로 묶인다
        assertThat(chat.history(c, crewId, ChatCursor.of(null, last, null)).messages())
                .singleElement()
                .satisfies(l -> assertThat(l.content()).isEqualTo("폴링으로 보냄"));
    }
}
