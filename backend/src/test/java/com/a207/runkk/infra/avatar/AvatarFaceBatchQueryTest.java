package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.avatar.Avatar;
import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.avatar.AvatarRepository;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.avatar.FacePart;
import com.a207.runkk.avatar.SdFaceParts;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.policy.SelfieLimit;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
class AvatarFaceBatchQueryTest {

    @Autowired AvatarRepository avatars;
    @Autowired AvatarQuery query;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManagerFactory factory;

    @Test
    void oneAndTenAuthorsUseOneFaceQuery() {
        List<Long> ids = new ArrayList<>();
        tx.executeWithoutResult(
                status -> {
                    for (long id = 90001; id <= 90010; id++) {
                        Avatar avatar = Avatar.create(id);
                        avatar.chooseDefault(DefaultCharacter.BEAR);
                        if (id == 90002) {
                            org.springframework.test.util.ReflectionTestUtils.setField(
                                    avatar, "defaultCharacter", null);
                            org.springframework.test.util.ReflectionTestUtils.setField(
                                    avatar, "headJobId", "ab12");
                        }
                        if (id == 90003) {
                            var parts = new EnumMap<FacePart, String>(FacePart.class);
                            for (FacePart part : FacePart.values()) {
                                parts.put(part, "face/old/" + part.name());
                            }
                            avatar.applySelfieFace(
                                    new SdFaceParts(parts), LocalDate.now(), new SelfieLimit(1));
                        }
                        avatars.save(avatar);
                        ids.add(id);
                    }
                    avatars.save(Avatar.create(90011));
                });
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            assertThat(query.faceKeysOf(List.of(ids.getFirst())))
                    .containsEntry(ids.getFirst(), "default:BEAR");
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

            statistics.clear();
            assertThat(query.faceKeysOf(ids))
                    .hasSize(10)
                    .containsEntry(90001L, "default:BEAR")
                    .containsEntry(90002L, "head:90002:ab12")
                    .containsEntry(90003L, "selfie:face/old");
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

            statistics.clear();
            assertThat(
                            query.faceKeysOf(
                                    java.util.Arrays.asList(
                                            ids.getFirst(), ids.getFirst(), null, 90011L, 99999L)))
                    .containsOnlyKeys(ids.getFirst());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            statistics.clear();
            assertThat(query.faceKeysOf(List.of())).isEmpty();
            assertThat(statistics.getPrepareStatementCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(previous);
            tx.executeWithoutResult(
                    status -> {
                        for (Long id : ids) {
                            avatars.deleteByMember(id);
                        }
                        avatars.deleteByMember(90011L);
                    });
        }
    }
}
