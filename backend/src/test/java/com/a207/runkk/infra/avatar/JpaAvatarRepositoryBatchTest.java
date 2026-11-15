package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.avatar.Avatar;
import com.a207.runkk.avatar.AvatarRepository;
import com.a207.runkk.avatar.AvatarView;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.web.WebTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 랭킹 줄 모습(S15P31A207-300)의 아바타 묶음 조회: 회원 수와 관계없이 질의 하나로 아바타·얼굴 부위·착용을 함께 읽는다(N+1 없음). 시험 문맥을 web
 * 시험과 함께 쓴다(H2).
 */
@DisplayName("아바타 묶음 조회(JPA)")
class JpaAvatarRepositoryBatchTest extends WebTestSupport {

    @Autowired AvatarRepository avatars;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManagerFactory entityManagerFactory;

    private List<Long> dressedAvatars(int count) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = signUp().memberId();
            ids.add(id);
            tx.executeWithoutResult(
                    status -> {
                        Avatar avatar = avatars.findForUpdate(id).orElseThrow();
                        avatar.chooseDefault(DefaultCharacter.RABBIT);
                        avatar.equip(1, ItemSlot.TOP);
                        avatar.equip(2, ItemSlot.SHOES);
                        avatars.save(avatar);
                    });
        }
        return ids;
    }

    /** 묶음 조회와 얼굴·착용 읽기까지 한 트랜잭션에서 실행한 SQL 문 수. */
    private long statementsToRead(List<Long> ids, List<AvatarView> out) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            tx.executeWithoutResult(
                    status -> avatars.findAllByMemberIds(ids).forEach(a -> out.add(a.view(0))));
            return stats.getPrepareStatementCount();
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    @Test
    @DisplayName("ID 묶음의 아바타를 얼굴·착용과 함께 질의 하나로 읽고, 없는 회원은 빠지며 행을 만들지 않는다")
    void readsAllInOneStatement() throws Exception {
        List<Long> three = dressedAvatars(3);
        long missing = signUp().memberId();
        List<Long> ids = new ArrayList<>(three);
        ids.add(missing);

        List<AvatarView> one = new ArrayList<>();
        long single = statementsToRead(List.of(three.get(0)), one);
        List<AvatarView> many = new ArrayList<>();
        long batch = statementsToRead(ids, many);

        assertThat(single).isEqualTo(1);
        assertThat(batch).as("회원 수에 비례하지 않는다").isEqualTo(1);
        assertThat(many).hasSize(3);
        assertThat(many)
                .allSatisfy(
                        v -> {
                            assertThat(v.faceKey()).isEqualTo("default:RABBIT");
                            assertThat(v.equipped()).containsOnlyKeys(ItemSlot.TOP, ItemSlot.SHOES);
                        });
        assertThat(avatars.findAllByMemberIds(List.of(missing))).isEmpty();
        assertThat(avatars.findAllByMemberIds(List.of())).isEmpty();
    }
}
