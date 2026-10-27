package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.PermissionDeniedException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 2.1.10.7 CrewPermissionPolicy 권한표(srs.md 3.0.3, F.5.1, D-98): 16 행동 × 4 관계 = 64칸
 * 결정표(TC-6060~TC-6072)와 표 불변·상수(TC-6073).
 */
@DisplayName("CrewPermissionPolicy 권한표 64칸")
class CrewPermissionPolicyTest {

    private static final String L = "L";
    private static final String LM = "LM";
    private static final String LMR = "LMR";
    private static final String ALL = "LMRO";

    private final CrewPermissionPolicy policy = new CrewPermissionPolicy();

    /** 행동별 허용 관계(L = 크루장, M = 운영진, R = 크루원, O = 크루 밖). */
    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("TC-6060", CrewAction.VIEW_PUBLIC_INFO, ALL),
                Arguments.of("TC-6061", CrewAction.EDIT_CREW, L),
                Arguments.of("TC-6062", CrewAction.TRANSFER_OR_DELETE, L),
                Arguments.of("TC-6063", CrewAction.APPOINT_MANAGER, L),
                Arguments.of("TC-6064", CrewAction.KICK, L),
                Arguments.of("TC-6065", CrewAction.VIEW_OR_DECIDE_JOIN, LM),
                Arguments.of("TC-6066", CrewAction.MANAGE_NOTICE, LM),
                Arguments.of("TC-6067", CrewAction.SET_GOAL, LM),
                Arguments.of("TC-6068", CrewAction.WRITE_POST, LMR),
                Arguments.of("TC-6069", CrewAction.RESPOND, LMR),
                Arguments.of("TC-6070", CrewAction.REPORT, LMR),
                Arguments.of("TC-6071", CrewAction.DELETE_ANY_CONTENT, LM),
                Arguments.of("TC-6071", CrewAction.DELETE_OWN_CONTENT, LMR),
                Arguments.of("TC-6072", CrewAction.VIEW_CREW_INTERNAL, LMR),
                Arguments.of("TC-6072", CrewAction.EDIT_OWN_CREW_PROFILE, LMR),
                Arguments.of("TC-6072", CrewAction.LEAVE, LMR));
    }

    private static boolean expected(String cell, CrewRelation relation) {
        return switch (relation) {
            case LEADER -> cell.contains("L");
            case MANAGER -> cell.contains("M");
            case MEMBER -> cell.contains("R");
            case OUTSIDER -> cell.contains("O");
        };
    }

    @ParameterizedTest(name = "{0} {1} 허용 {2}")
    @MethodSource("table")
    @DisplayName("행동마다 L·M·R·O 네 칸: isAllowed와 check가 같은 표를 답한다")
    void eachCell(String tc, CrewAction action, String allowed) {
        for (CrewRelation relation : CrewRelation.values()) {
            boolean expected = expected(allowed, relation);
            assertThat(policy.isAllowed(relation, action))
                    .as(tc + " " + action + " × " + relation)
                    .isEqualTo(expected);
            if (expected) {
                assertThatCode(() -> policy.check(relation, action)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> policy.check(relation, action))
                        .isInstanceOf(PermissionDeniedException.class)
                        .hasMessage("권한 없음");
            }
        }
    }

    @Test
    @DisplayName("TC-6073: 상수 16개가 모두 표에 있고(64칸) 표는 불변이며 v0.10 이름은 없다")
    void tableIsCompleteAndImmutable() {
        assertThat(CrewAction.values()).hasSize(16);
        assertThat(table().map(a -> a.get()[1]).toList())
                .containsExactlyInAnyOrder((Object[]) CrewAction.values());
        assertThat(policy.table()).containsOnlyKeys(CrewAction.values());
        assertThatThrownBy(
                        () ->
                                policy.table()
                                        .put(CrewAction.KICK, EnumSet.allOf(CrewRelation.class)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy.table().get(CrewAction.KICK).add(CrewRelation.MEMBER))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(Arrays.stream(CrewAction.values()).map(Enum::name))
                .doesNotContain("VOTE_OR_JOIN_CREW_RUN", "DELETE_ANY_POST", "DELETE_OWN_POST");
    }
}
