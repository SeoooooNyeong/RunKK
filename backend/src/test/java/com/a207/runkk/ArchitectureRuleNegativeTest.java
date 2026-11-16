package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import archfixture.ArchFixtureEntityWithSetter;
import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.audit.ArchFixtureAuditUsesWeb;
import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.battle.live.ArchFixtureLiveMessage;
import com.a207.runkk.common.ArchFixtureCommonPort;
import com.a207.runkk.crew.ArchFixtureCrewImplementsReportPort;
import com.a207.runkk.crew.ArchFixtureCrewUsesChat;
import com.a207.runkk.crew.ArchFixtureCrewUsesReportTarget;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.PostKind;
import com.a207.runkk.crew.chat.ArchFixtureChatUsesAvatar;
import com.a207.runkk.crew.chat.ArchFixtureChatUsesRealtime;
import com.a207.runkk.crew.chat.ArchFixtureChatUsesRunimal;
import com.a207.runkk.crew.chat.ChatCursor;
import com.a207.runkk.crew.chat.CrewChatMessageRepository;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.crew.chat.live.ArchFixtureChatLiveUsesCrew;
import com.a207.runkk.crew.chat.live.CrewChatPosted;
import com.a207.runkk.infra.audit.ArchFixtureInfraAuditUsesPolicy;
import com.a207.runkk.infra.chat.JpaCrewChatMessageRepository;
import com.a207.runkk.member.ArchFixtureMemberCallsPolicyQuery;
import com.a207.runkk.member.ArchFixtureMemberUsesPolicySetting;
import com.a207.runkk.policy.ArchFixturePolicyRepository;
import com.a207.runkk.realtime.ArchFixtureRealtimeUsesChatLive;
import com.a207.runkk.realtime.ArchFixtureRealtimeUsesLive;
import com.a207.runkk.realtime.ChatPresenceRegistry;
import com.a207.runkk.report.ArchFixtureReportUsesCrew;
import com.a207.runkk.report.ReportedContentPort;
import com.a207.runkk.report.target.ChatReportAdapter;
import com.a207.runkk.report.target.CrewPostReportAdapter;
import com.a207.runkk.reward.ArchFixtureRewardUsesBattleEntity;
import com.a207.runkk.runimal.RunimalQuery;
import com.a207.runkk.web.admin.ArchFixtureAdminUsesRanking;
import com.a207.runkk.web.admin.ranking.RankingAdminController;
import com.a207.runkk.web.admin.setting.ArchFixtureSettingUsesRepository;
import com.a207.runkk.web.attempt.AttemptLimiter;
import com.a207.runkk.web.crew.ArchFixtureWebCrewCallsReportedView;
import com.a207.runkk.web.error.ArchFixtureWebValue;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** 음성 시험: 시험 소스에만 있는 위반 클래스로 ArchitectureTest의 규칙이 실제로 위반을 잡는지 확인한다. */
@org.junit.jupiter.api.DisplayName("ArchUnit 규칙 음성 시험(위반 고정 클래스를 잡는다)")
class ArchitectureRuleNegativeTest {

    private static boolean violates(ArchRule rule, Class<?>... fixtures) {
        JavaClasses imported = new ClassFileImporter().importClasses(fixtures);
        return rule.evaluate(imported).hasViolation();
    }

    @Test
    void rule01_catchesDependencyMissingFrom12_2() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureInfraAuditUsesPolicy.class,
                                ArchFixturePolicyRepository.class))
                .isTrue();
    }

    @Test
    void rule01_allowsDependencyListedIn12_2() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureSettingUsesRepository.class,
                                ArchFixturePolicyRepository.class))
                .isFalse();
    }

    @Test
    void rule03_catchesDomainImportingWeb() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_3_CORE_DOES_NOT_KNOW_OUTER_LAYERS,
                                ArchFixtureAuditUsesWeb.class,
                                ArchFixtureWebValue.class))
                .isTrue();
    }

    @Test
    void rule04_catchesRepositoryUsedOutsideOwnerAndItsInfra() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_4_STORAGE_PORTS_STAY_INSIDE,
                                ArchFixtureSettingUsesRepository.class,
                                ArchFixturePolicyRepository.class))
                .isTrue();
    }

    @Test
    void rule06_catchesPortInterfaceInCommon() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_6_COMMON_STAYS_PLAIN,
                                ArchFixtureCommonPort.class))
                .isTrue();
    }

    @Test
    void rule07_catchesRealtimeImportingLiveMessage() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_7_LIVE_MESSAGES_STAY_INSIDE,
                                ArchFixtureRealtimeUsesLive.class,
                                ArchFixtureLiveMessage.class))
                .isTrue();
    }

    @Test
    void rule11_catchesMemberUsingOtherPolicyType() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_11_STABILITY_EXCEPTIONS_STAY_NARROW,
                                ArchFixtureMemberUsesPolicySetting.class))
                .isTrue();
    }

    @Test
    void rule11_catchesMemberCallingOtherPolicyQuery() {
        String report =
                ArchitectureTest.RULE_11_STABILITY_EXCEPTIONS_STAY_NARROW
                        .evaluate(
                                new ClassFileImporter()
                                        .importClasses(ArchFixtureMemberCallsPolicyQuery.class))
                        .getFailureReport()
                        .toString();
        assertThat(report).contains("OperationPolicy.matchCondition()");
    }

    @Test
    void rule11_catchesRewardUsingOtherBattleType() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_11_STABILITY_EXCEPTIONS_STAY_NARROW,
                                ArchFixtureRewardUsesBattleEntity.class))
                .isTrue();
    }

    @Test
    void rule11_allowsListedTypesAndCalls() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_11_STABILITY_EXCEPTIONS_STAY_NARROW,
                                com.a207.runkk.member.TierLevelService.class,
                                com.a207.runkk.reward.RewardReconcileService.class))
                .isFalse();
    }

    @Test
    void rule09_catchesPublicSetterOnEntity() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_9_ENTITIES_HAVE_NO_PUBLIC_SETTERS,
                                ArchFixtureEntityWithSetter.class))
                .isTrue();
    }

    @Test
    void rule10_catchesPublicFieldOnEntity() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_10_NO_PUBLIC_FIELDS,
                                ArchFixtureEntityWithSetter.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6209 ③④: crew.chat.live가 crew를, realtime이 crew.chat.live를 import하면 규칙 7 위반")
    void rule07_catchesChatLiveViolations() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_7_LIVE_MESSAGES_STAY_INSIDE,
                                ArchFixtureChatLiveUsesCrew.class))
                .isTrue();
        assertThat(
                        violates(
                                ArchitectureTest.RULE_7_LIVE_MESSAGES_STAY_INSIDE,
                                ArchFixtureRealtimeUsesChatLive.class,
                                CrewChatPosted.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6209 ⑤: crew가 crew.chat을 import하면 규칙 1 위반(crew.chat → crew 한 방향)")
    void rule01_catchesCrewImportingChat() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureCrewUsesChat.class,
                                ChatCursor.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "설계 5.31 [임시:D-성-231]: crew.chat → avatar만 새로 허용되고 다른 새 화살표(crew.chat → runimal)는 규칙 1"
                    + " 위반이다")
    void rule01_allowsChatToAvatarOnly() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureChatUsesAvatar.class,
                                AvatarQuery.class))
                .isFalse();
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureChatUsesRunimal.class,
                                RunimalQuery.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6838: crew.chat이 realtime.ChatPresenceRegistry를 import하면 규칙 3 위반")
    void rule03_catchesChatImportingRealtime() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_3_CORE_DOES_NOT_KNOW_OUTER_LAYERS,
                                ArchFixtureChatUsesRealtime.class,
                                ChatPresenceRegistry.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("infra.chat은 crew.chat 저장 포트를 구현할 수 있다(규칙 4, N15)")
    void rule04_allowsInfraChatForCrewChat() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_4_STORAGE_PORTS_STAY_INSIDE,
                                JpaCrewChatMessageRepository.class,
                                CrewChatMessageRepository.class))
                .isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6206 ①·TC-6831: crew가 report 포트를 구현하거나 report가 crew를 import하거나 web.admin이 랭킹을 부르면"
                    + " 규칙 1 위반")
    void rule01_catchesRemovedReportAndRankingArrows() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureCrewImplementsReportPort.class,
                                ReportedContentPort.class))
                .isTrue();
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureReportUsesCrew.class,
                                PostKind.class))
                .isTrue();
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                ArchFixtureAdminUsesRanking.class,
                                RankingService.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6206 ②④: web.crew가 신고용 공개 연산을 부르거나 crew가 report.target을 import하면 규칙 8 위반")
    void rule08_catchesReportOnlyCallsAndAssemblyImports() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_8_DOMAIN_DOES_NOT_KNOW_OPERATOR,
                                ArchFixtureWebCrewCallsReportedView.class,
                                CrewPostService.class))
                .isTrue();
        assertThat(
                        violates(
                                ArchitectureTest.RULE_8_DOMAIN_DOES_NOT_KNOW_OPERATOR,
                                ArchFixtureCrewUsesReportTarget.class,
                                CrewPostReportAdapter.class))
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6206 ③: report.target은 crew 신고용 공개 연산을 부를 수 있다(규칙 1 V1~V3·규칙 8 통과)")
    void reportTarget_mayCallReportOnlyOperations() {
        assertThat(
                        violates(
                                ArchitectureTest.RULE_8_DOMAIN_DOES_NOT_KNOW_OPERATOR,
                                CrewPostReportAdapter.class,
                                ChatReportAdapter.class,
                                CrewPostService.class,
                                CrewChatService.class))
                .isFalse();
        assertThat(
                        violates(
                                ArchitectureTest.RULE_1_ONLY_LISTED_DEPENDENCIES,
                                CrewPostReportAdapter.class,
                                ChatReportAdapter.class,
                                ReportedContentPort.class))
                .isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6205: report.target·web.admin.ranking·crew.chat.live·web.attempt 클래스는 가장 긴 5.1.1"
                    + " 이름의 slice")
    void sliceAssignment_usesLongestDesignName() {
        JavaClasses imported =
                new ClassFileImporter()
                        .importClasses(
                                CrewPostReportAdapter.class,
                                RankingAdminController.class,
                                CrewChatPosted.class,
                                AttemptLimiter.class);
        assertThat(ArchitectureTest.sliceOf(imported.get(CrewPostReportAdapter.class)))
                .contains("report.target");
        assertThat(ArchitectureTest.sliceOf(imported.get(RankingAdminController.class)))
                .contains("web.admin.ranking");
        assertThat(ArchitectureTest.sliceOf(imported.get(CrewChatPosted.class)))
                .contains("crew.chat");
        assertThat(ArchitectureTest.sliceOf(imported.get(AttemptLimiter.class)))
                .contains("web.attempt");
    }
}
