package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.ItemSlot;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기록의 그 시점 모습 읽기(설계 5.31 B, [임시:D-성-251]). 옛 기록은 채우지 않아(백필 없음) 비어 있다. */
@DisplayName("running RunQueryService.lookOf")
class RunQueryServiceTest {

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final RunQueryService query =
            new RunQueryService(records, new InMemoryRunningRepositories.Books(), null);

    @Test
    @DisplayName("모습이 없는 옛 기록은 비어 있다. 없는 기록 ID도 비어 있다")
    void 모습이_없는_옛_기록은_비어_있다() {
        records.save(RunFixtures.valid5k(id(1)));

        assertThat(query.lookOf(id(1))).isEmpty();
        assertThat(query.lookOf(id(9))).isEmpty();
    }

    @Test
    @DisplayName("모습을 붙여 저장한 기록은 같은 값을 돌려준다: 안 입은 부위와 고르지 않은 얼굴은 없는 그대로다")
    void 모습이_있는_기록은_그_값을_준다() {
        AvatarLook full =
                new AvatarLook(
                        "MALE",
                        Map.of(ItemSlot.TOP, 12L, ItemSlot.BOTTOM, 21L, ItemSlot.SHOES, 31L),
                        2,
                        100,
                        "default:FOX");
        AvatarLook bare = new AvatarLook("FEMALE", Map.of(), 0, 0, null);
        RunRecord first = RunFixtures.valid5k(id(1));
        first.attachLook(full);
        records.save(first);
        RunRecord second = RunFixtures.valid5k(id(2));
        second.attachLook(bare);
        second.attachLook(null);
        records.save(second);

        assertThat(query.lookOf(id(1))).contains(full);
        assertThat(query.lookOf(id(2))).contains(bare);
    }

    @Test
    @DisplayName("기록에 남기는 부위는 상의·하의·신발뿐이다: 다른 부위(모자)는 저장하지 않는다")
    void 모자는_기록에_남기지_않는다() {
        RunRecord record = RunFixtures.valid5k(id(1));
        record.attachLook(
                new AvatarLook("MALE", Map.of(ItemSlot.TOP, 12L, ItemSlot.HAT, 99L), 1, 10, null));
        records.save(record);

        assertThat(query.lookOf(id(1)).orElseThrow().equipped())
                .containsExactly(Map.entry(ItemSlot.TOP, 12L));
    }
}
