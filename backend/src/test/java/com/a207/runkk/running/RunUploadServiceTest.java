package com.a207.runkk.running;

import static com.a207.runkk.running.RunFixtures.id;
import static com.a207.runkk.running.RunFixtures.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기록 저장 시점의 아바타 모습(설계 5.31 B, [임시:D-성-250], [임시:FR-성-26]). RunUploadService가 RunRecord를 저장하기 직전에
 * AvatarLookSource로 읽어 붙이고, 읽다가 실패해도 저장은 계속한다.
 */
@DisplayName("running RunUploadService 기록 시점 모습")
class RunUploadServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final AvatarLook LOOK =
            new AvatarLook(
                    "FEMALE", Map.of(ItemSlot.TOP, 12L, ItemSlot.SHOES, 31L), 1, 42, "head:7:ab12");

    private final InMemoryRunningRepositories.Records records =
            new InMemoryRunningRepositories.Records();
    private final InMemoryRunningRepositories.Books books = new InMemoryRunningRepositories.Books();
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final List<DomainEvent> published = new ArrayList<>();
    private final List<Long> asked = new ArrayList<>();
    private AvatarLookSource source =
            memberId -> {
                asked.add(memberId);
                return Optional.of(LOOK);
            };

    private RunUploadService uploads() {
        return new RunUploadService(
                records,
                books,
                (point, at) -> Optional.empty(),
                new MemberQueryService(members),
                memberId -> source.currentLookOf(memberId),
                published::add,
                RunningServicesTest.NO_TX,
                Clock.system(SEOUL));
    }

    private long member() {
        return members.save(MemberFixtures.member("모습러너" + (members.size() + 1))).getId();
    }

    @Test
    @DisplayName("저장하면 그 시점 모습이 기록에 붙는다")
    void 저장하면_그_시점_모습이_기록에_붙는다() {
        long m = member();

        UploadResult result =
                uploads().upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));

        assertThat(result.alreadyStored()).isFalse();
        assertThat(asked).containsExactly(m);
        assertThat(records.findById(id(1)).orElseThrow().look()).contains(LOOK);
    }

    @Test
    @DisplayName("모습을 읽다 실패해도 기록은 저장된다: 모습만 비어 있다. 읽을 모습이 없을 때도 같다")
    void 모습을_읽다_실패해도_기록은_저장된다() {
        long m = member();
        source =
                memberId -> {
                    throw new IllegalStateException("avatar down");
                };

        uploads().upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));

        RunRecord failed = records.findById(id(1)).orElseThrow();
        assertThat(failed.isValid()).isTrue();
        assertThat(failed.look()).isEmpty();
        assertThat(published).hasSize(1);

        source = memberId -> Optional.empty();
        uploads().upload(m, new RunUploadCommand(upload(id(2), 5_000, 1_500)));
        assertThat(records.findById(id(2)).orElseThrow().look()).isEmpty();
    }

    @Test
    @DisplayName("워치 업로드도 같은 모습을 붙인다: 워치 단독 기록(startDevice WATCH)도 같은 저장 길을 지난다")
    void 워치_업로드도_같은_모습을_붙인다() {
        long m = member();
        RunUpload fromWatch =
                RunFixtures.withStartDevice(upload(id(1), 5_000, 1_500), StartDevice.WATCH);

        uploads().upload(m, new RunUploadCommand(fromWatch));

        RunRecord saved = records.findById(id(1)).orElseThrow();
        assertThat(saved.getStartDevice()).isEqualTo(StartDevice.WATCH);
        assertThat(saved.look()).contains(LOOK);
    }

    @Test
    @DisplayName("같은 기록을 다시 보내면 모습을 다시 읽지 않는다(이미 저장된 기록은 그대로다)")
    void 재전송은_모습을_다시_읽지_않는다() {
        long m = member();
        uploads().upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));
        asked.clear();

        UploadResult again = uploads().upload(m, new RunUploadCommand(upload(id(1), 5_000, 1_500)));

        assertThat(again.alreadyStored()).isTrue();
        assertThat(asked).isEmpty();
    }
}
