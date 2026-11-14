package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.BodyShapeRule;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.SkinToneRule;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지금 모습 읽기(설계 5.31 B, [임시:D-성-246]·[임시:D-성-248]). 아바타 응답의 equipped, 내 러너 응답의 bodyVariant·runAmount,
 * 얼굴 키, 표시 성별을 한 값으로 묶는다.
 */
@DisplayName("avatar AvatarLookReader")
class AvatarLookReaderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 20, 0);
    private static final long M = 5;
    private static final long OTHER = 6;

    private final AvatarFakes.Avatars avatars = new AvatarFakes.Avatars();
    private final RunQueryService runs = mock(RunQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RunnerService runner =
            new RunnerService(
                    avatars,
                    runs,
                    policy,
                    new AppearanceCalculator(),
                    AvatarFakes.transactions(),
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    private final AvatarLookReader reader =
            new AvatarLookReader(avatars, runner, members, AvatarFakes.transactions());

    @BeforeEach
    void rules() {
        when(policy.bodyShapeRule()).thenReturn(new BodyShapeRule(List.of(0, 1, 3, 5), 4));
        when(policy.skinToneRule())
                .thenReturn(
                        new SkinToneRule(
                                List.of(
                                        LocalTime.of(4, 0),
                                        LocalTime.of(7, 0),
                                        LocalTime.of(11, 0),
                                        LocalTime.of(17, 0),
                                        LocalTime.of(21, 0))));
        when(runs.dailyStats(anyLong(), any())).thenReturn(Map.of());
        when(runs.recentStartTimes(anyLong(), anyInt())).thenReturn(List.of());
        when(members.shownGendersOf(anyCollection()))
                .thenAnswer(
                        inv -> {
                            Map<Long, Gender> out = new HashMap<>();
                            for (Long id : inv.<Collection<Long>>getArgument(0)) {
                                out.put(id, id == M ? Gender.FEMALE : Gender.MALE);
                            }
                            return out;
                        });
    }

    @Test
    @DisplayName("지금 모습: 표시 성별, 상의·하의·신발(모자는 뺀다), 3단계 체형, 러닝량, 얼굴 키")
    void currentLook_bundlesAvatarAndRunnerValues() {
        Avatar avatar = Avatar.create(M);
        avatar.chooseDefault(DefaultCharacter.FOX);
        avatar.equip(12, ItemSlot.TOP);
        avatar.equip(31, ItemSlot.SHOES);
        avatar.equip(99, ItemSlot.HAT);
        avatar.updateAppearance(BodyShape.STAGE1, avatar.skinTone());
        avatars.save(avatar);
        LocalDate today = NOW.toLocalDate();
        when(runs.dailyStats(anyLong(), any()))
                .thenReturn(
                        Map.of(
                                today,
                                new DayStats(
                                        today,
                                        Distance.ofMeters(15_000),
                                        Duration.ofMinutes(90),
                                        2)));

        AvatarLook look = reader.currentLookOf(M).orElseThrow();

        assertThat(look.gender()).isEqualTo("FEMALE");
        assertThat(look.equipped())
                .containsExactly(Map.entry(ItemSlot.TOP, 12L), Map.entry(ItemSlot.SHOES, 31L));
        assertThat(look.bodyVariant()).isEqualTo(2);
        assertThat(look.runAmount()).isEqualTo(50);
        assertThat(look.faceKey()).isEqualTo("default:FOX");
    }

    @Test
    @DisplayName("아무것도 고르지 않은 회원: 옷 없음, 얼굴 키 null, 러닝량 0")
    void currentLook_ofFreshMember() {
        AvatarLook look = reader.currentLookOf(OTHER).orElseThrow();

        assertThat(look.gender()).isEqualTo("MALE");
        assertThat(look.equipped()).isEmpty();
        assertThat(look.runAmount()).isZero();
        assertThat(look.faceKey()).isNull();
    }

    @Test
    @DisplayName("AvatarQuery.looksOf: 회원마다 모습 하나, null과 중복 ID는 건너뛴다")
    void looksOf_readsEachMemberOnce() {
        Map<Long, AvatarLook> looks =
                new AvatarQuery(avatars, reader)
                        .looksOf(java.util.Arrays.asList(M, null, OTHER, M));

        assertThat(looks).containsOnlyKeys(M, OTHER);
        assertThat(looks.get(M).gender()).isEqualTo("FEMALE");
        assertThat(looks.get(OTHER).gender()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("AvatarLook.withGender는 성별만 바꾼 사본이다")
    void withGender_copiesOtherValues() {
        AvatarLook look = new AvatarLook("MALE", Map.of(ItemSlot.TOP, 12L), 1, 42, "head:7:ab12");

        assertThat(look.withGender("FEMALE"))
                .isEqualTo(
                        new AvatarLook("FEMALE", Map.of(ItemSlot.TOP, 12L), 1, 42, "head:7:ab12"));
        assertThat(look.gender()).isEqualTo("MALE");
    }
}
