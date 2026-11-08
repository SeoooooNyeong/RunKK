package com.a207.runkk;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.Slice;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

/**
 * 설계서 13.1 ArchUnit 규칙 12개(14장 "통합 — 구조", v0.11 검증으로 (11)·(12) 추가). 아직 클래스가 없는 패키지 때문에 규칙이 거짓 실패하지
 * 않도록 모든 규칙에 allowEmptyShould(true)를 둔다. 규칙이 실제 위반을 잡는지는 {@link ArchitectureRuleNegativeTest}가
 * 확인한다.
 */
@org.junit.jupiter.api.DisplayName("ArchUnit 13.1 규칙 12개")
class ArchitectureTest {

    static final String ROOT = "com.a207.runkk";

    /**
     * 5.1.1 서버 패키지 53개 + batch.reward(코드 리뷰 2차 M4 결정: 리워드 일 단위 보정) = 54개, + v0.11 S0 계약 고정에서 만든
     * 5개(crew.chat, report, web.chat, web.report, web.admin.report) = 59개, + S3b infra.chat(N15),
     * S3a web.attempt(D-132) = 61개, + S3c
     * infra.report(N16)·report.target(D-135)·web.admin.ranking(D-136) = 64개, + v0.14
     * batch.runimal(D-246, 유형 비율 0시 작업) = 65개(12장 최종 목록, design-review.md 18장 구조 시험 고정 조건 1,
     * TC-9856), + 핫픽스 v0.11.4 이벤트 event·infra.event·web.event·web.admin.event(S15P31A207-275) =
     * 69개, + 운영자 공지 web.admin.notification(S15P31A207-275) = 70개.
     */
    static final List<String> PACKAGES =
            List.of(
                    "web.auth",
                    "web.me",
                    "web.notification",
                    "web.run",
                    "web.record",
                    "web.battle",
                    "web.crew",
                    "web.runimal",
                    "web.avatar",
                    "web.admin",
                    "web.admin.celebrity",
                    "web.admin.setting",
                    "web.admin.audit",
                    "web.admin.report",
                    "web.admin.ranking",
                    "web.admin.event",
                    "web.admin.notification",
                    "web.event",
                    "web.chat",
                    "web.report",
                    "web.attempt",
                    "web.error",
                    "web.runbody",
                    "realtime",
                    "batch.battle",
                    "batch.crew",
                    "batch.analysis",
                    "batch.avatar",
                    "batch.running",
                    "batch.member",
                    "batch.aiprivacy",
                    "batch.runbody",
                    "batch.audit",
                    "batch.notification",
                    "batch.reward",
                    "batch.runimal",
                    "bootstrap",
                    "profile",
                    "operator",
                    "report.target",
                    "member",
                    "running",
                    "runimal",
                    "battle",
                    "reward",
                    "avatar",
                    "crew",
                    "crew.chat",
                    "report",
                    "analysis",
                    "event",
                    "policy",
                    "notification",
                    "audit",
                    "aiprivacy",
                    "runbody",
                    "common",
                    "infra.member",
                    "infra.running",
                    "infra.analysis",
                    "infra.battle",
                    "infra.crew",
                    "infra.runimal",
                    "infra.avatar",
                    "infra.reward",
                    "infra.policy",
                    "infra.notification",
                    "infra.chat",
                    "infra.report",
                    "infra.event",
                    "infra.audit",
                    "infra.aiprivacy",
                    "infra.runbody",
                    "infra.integration.auth",
                    "infra.integration.ai",
                    "infra.integration.push",
                    "infra.integration.weather");

    /**
     * 도메인·조립 패키지(5.2 조립·응용+도메인·도메인 공통 계층, 핵심 14개 + v0.11 crew.chat·report·report.target = 17개, +
     * v0.11.4 event = 18개).
     */
    static final List<String> CORE =
            List.of(
                    "profile",
                    "operator",
                    "report.target",
                    "member",
                    "running",
                    "runimal",
                    "battle",
                    "reward",
                    "avatar",
                    "crew",
                    "crew.chat",
                    "report",
                    "analysis",
                    "event",
                    "policy",
                    "notification",
                    "audit",
                    "aiprivacy",
                    "runbody",
                    "common");

    /** 12.2 의존 목록(D1~D74, D44·D67의 → common 포함). 키 패키지는 값 패키지만 import할 수 있다. */
    static final Map<String, Set<String>> ALLOWED = allowedDependencies();

    private static Map<String, Set<String>> allowedDependencies() {
        Map<String, Set<String>> m = new HashMap<>();
        // 핵심 패키지 (D1~D43, 12.3 행렬)
        m.put("member", Set.of("policy", "audit"));
        m.put("aiprivacy", Set.of("member", "audit"));
        // 05-19 5절(D-441·D-444·D-440): RunRecordSaved·bodyInputOf, 출생연도, RUNBODY_ENABLED
        m.put("runbody", Set.of("member", "running", "policy"));
        m.put("running", Set.of("member", "audit"));
        m.put("runimal", Set.of("member", "running", "policy"));
        m.put("battle", Set.of("member", "running", "policy", "notification", "audit"));
        m.put("reward", Set.of("member", "running", "battle", "policy"));
        m.put("avatar", Set.of("member", "running", "reward", "policy", "notification"));
        m.put("crew", Set.of("member", "running", "runimal", "policy", "notification", "audit"));
        m.put("analysis", Set.of("member", "running", "crew", "reward", "policy", "notification"));
        m.put("profile", Set.of("member", "running", "runimal", "avatar", "crew"));
        m.put("operator", Set.of("member", "policy", "audit")); // + audit: 운영자 로그인 감사(L25)
        m.put("policy", Set.of("audit"));
        // 운영자 공지 감사(S15P31A207-275, NoticeService → AuditService)
        m.put("notification", Set.of("member", "policy", "audit"));
        m.put("audit", Set.of());
        m.put("common", Set.of());
        // v0.11 (부록 F F.6.2 N1~N4, N7~N9. N5·N6은 설계 검증 D-135로 report.target으로 옮겨 없다)
        m.put("crew.chat", Set.of("crew", "member", "notification", "audit"));
        m.put("report", Set.of("notification", "audit", "member"));
        // 신고 대상 조립(V1~V3, D-135): crew·crew.chat의 신고용 공개 연산으로 report.ReportedContentPort를 구현
        m.put("report.target", Set.of("crew", "crew.chat", "report"));
        // 표현 (D45~D54, D63, D64, D68~D70)
        m.put("web.auth", Set.of("member", "web.attempt")); // + V6(D-132)
        m.put("web.me", Set.of("member", "profile"));
        m.put("web.notification", Set.of("notification"));
        m.put("web.run", Set.of("running"));
        // v0.14 W1·W2: 기록 목록 대결 결과(BattleHistoryService.outcomesOf), 기록
        // 삭제(RunDeletionService.delete)
        m.put("web.record", Set.of("analysis", "battle", "running"));
        // v0.14 W5: 대결 결과의 평가 표시(RunQueryService.evaluationOf, D-291·D-300)
        m.put("web.battle", Set.of("battle", "reward", "running"));
        m.put("web.crew", Set.of("crew", "runimal", "web.attempt")); // + V9(D-132)
        m.put("web.runimal", Set.of("runimal"));
        m.put("web.avatar", Set.of("avatar", "reward"));
        m.put("web.admin", Set.of("operator", "web.attempt")); // + V7
        // D77: 운영자 랭킹 다시 계산(RankingService.recompute, 감사 RANKING_RECOMPUTED). D-136으로 web.admin에서
        // 옮겼다
        m.put("web.admin.ranking", Set.of("analysis", "audit"));
        m.put("web.admin.celebrity", Set.of("battle"));
        m.put("web.admin.setting", Set.of("policy"));
        m.put("web.admin.audit", Set.of("audit"));
        m.put("web.error", Set.of());
        // v0.11 (N12~N14), web.chat → web.attempt(V8, D-132)
        m.put("web.chat", Set.of("crew.chat", "web.attempt"));
        // web.attempt는 java.*만 import한다(Fan-Out 0, 12.2 D67 예외, 13.1 규칙 12)
        m.put("web.attempt", Set.of());
        m.put("web.report", Set.of("report"));
        m.put("web.admin.report", Set.of("report"));
        // 핫픽스 v0.11.4 이벤트(S15P31A207-275): 응모 계산(battle, running), 코치 승리 포인트(reward), 관리 감사(audit),
        // 응모자 명단 닉네임(member)
        m.put("event", Set.of("battle", "running", "reward", "audit", "member"));
        m.put("web.event", Set.of("event"));
        m.put("web.admin.event", Set.of("event"));
        m.put("web.admin.notification", Set.of("notification"));
        // 05-19 5절 web.runbody → runbody, + runimal(요약 G-14의 나의 러닝 유형, web.crew와 같이 RunimalQuery만)
        m.put("web.runbody", Set.of("runbody", "runimal"));
        m.put("realtime", Set.of("battle", "crew", "crew.chat", "member")); // + crew.chat(N11)
        // 구동 (D55~D57, D65, D71~D74): batch.<응용> → 같은 이름의 패키지 하나
        for (String app :
                List.of(
                        "battle",
                        "crew",
                        "analysis",
                        "avatar",
                        "running",
                        "member",
                        "aiprivacy",
                        "runbody", // 05-19 D-443 RunBodyPollJob(pollDue 5초)
                        "audit",
                        "notification",
                        "reward",
                        "runimal")) { // v0.14 W3·W4: batch.runimal → runimal(RunimalShareJob),
            // common
            m.put("batch." + app, Set.of(app));
        }
        m.put("bootstrap", Set.of());
        // 인프라 (D58~D62): infra.<도메인> → 같은 이름의 도메인
        for (String domain :
                List.of(
                        "member",
                        "aiprivacy",
                        "runbody",
                        "running",
                        "analysis",
                        "battle",
                        "crew",
                        "runimal",
                        "avatar",
                        "reward",
                        "policy",
                        "notification",
                        "event",
                        "audit")) {
            m.put("infra." + domain, Set.of(domain));
        }
        m.put("infra.chat", Set.of("crew.chat")); // v0.11 N15
        m.put("infra.report", Set.of("report")); // v0.11 N16
        m.put("infra.integration.auth", Set.of("member", "operator"));
        m.put("infra.integration.ai", Set.of("analysis", "avatar", "aiprivacy", "runbody"));
        m.put("infra.integration.push", Set.of("notification"));
        m.put("infra.integration.weather", Set.of("running"));
        // 모든 패키지 → common (D44, D53, D66, D67)
        m.replaceAll(
                (from, to) ->
                        from.equals("common") || from.equals("web.attempt")
                                ? to
                                : Stream.concat(to.stream(), Stream.of("common"))
                                        .collect(Collectors.toUnmodifiableSet()));
        return Map.copyOf(m);
    }

    /**
     * 클래스 패키지에 맞는 가장 긴 5.1.1 이름을 고른다. battle.live·crew.live는 5.1.1 이름이 아니므로 상위(battle, crew)로 합쳐진다.
     */
    static Optional<String> sliceOf(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            return Optional.empty();
        }
        String relative = pkg.substring(ROOT.length() + 1);
        return PACKAGES.stream()
                .filter(name -> relative.equals(name) || relative.startsWith(name + "."))
                .max(Comparator.comparingInt(String::length));
    }

    static final SliceAssignment DESIGN_PACKAGES =
            new SliceAssignment() {
                @Override
                public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                    return sliceOf(javaClass)
                            .map(SliceIdentifier::of)
                            .orElse(SliceIdentifier.ignore());
                }

                @Override
                public String getDescription() {
                    return "5.1.1 서버 패키지 64개(가장 긴 이름, *.live는 상위로)";
                }
            };

    private static boolean listedIn12_2(Dependency dependency) {
        Optional<String> from = sliceOf(dependency.getOriginClass());
        Optional<String> to = sliceOf(dependency.getTargetClass());
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
            return true;
        }
        return ALLOWED.getOrDefault(from.get(), Set.of()).contains(to.get());
    }

    private static final ArchCondition<Slice> DEPEND_ONLY_ON_LISTED_PACKAGES =
            new ArchCondition<>("12.2 의존 목록에 있는 패키지만 import한다") {
                @Override
                public void check(Slice slice, ConditionEvents events) {
                    for (Dependency dependency : slice.getDependenciesFromSelf()) {
                        if (!listedIn12_2(dependency)) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            dependency, dependency.getDescription()));
                        }
                    }
                }
            };

    private static String[] packagesOf(List<String> names) {
        return names.stream().map(name -> ROOT + "." + name + "..").toArray(String[]::new);
    }

    private static final String[] CORE_PACKAGES = packagesOf(CORE);

    // (1) 12.2 의존 목록에 없는 패키지 사이 import 0
    static final ArchRule RULE_1_ONLY_LISTED_DEPENDENCIES =
            slices().assignedFrom(DESIGN_PACKAGES)
                    .should(DEPEND_ONLY_ON_LISTED_PACKAGES)
                    .allowEmptyShould(true)
                    .because("12.2 의존 목록 밖 import는 설계 불일치다(13.1 규칙 1)");

    // (2) 순환 0 — 하위 패키지는 Fan-In이 0이라 최상위 slice로 충분하다
    static final ArchRule RULE_2_NO_CYCLES =
            slices().matching(ROOT + ".(*)..")
                    .should()
                    .beFreeOfCycles()
                    .allowEmptyShould(true)
                    .because("패키지 사이 순환이 없어야 한다(13.1 규칙 2)");

    // (3) 도메인·조립 패키지 → web.*·realtime·batch.*·infra.* import 0
    static final ArchRule RULE_3_CORE_DOES_NOT_KNOW_OUTER_LAYERS =
            noClasses()
                    .that()
                    .resideInAnyPackage(CORE_PACKAGES)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + ".web..",
                            ROOT + ".realtime..",
                            ROOT + ".batch..",
                            ROOT + ".infra..")
                    .allowEmptyShould(true)
                    .because("하위 계층이 상위 계층을 import하지 않는다(5.2, 13.1 규칙 3)");

    /** 도메인 패키지의 저장 구현 패키지. 대개 infra.<도메인>이고 crew.chat은 infra.chat이다(5.1.1, N15). */
    static String infraOf(String owner) {
        return owner.equals("crew.chat") ? "infra.chat" : "infra." + owner;
    }

    private static final DescribedPredicate<JavaClass> STORAGE_PORT =
            DescribedPredicate.describe(
                    "…Repository·…Store·QuotaCounter·MatchQueue 인터페이스",
                    javaClass ->
                            javaClass.isInterface()
                                    && (javaClass.getSimpleName().endsWith("Repository")
                                            || javaClass.getSimpleName().endsWith("Store")
                                            || javaClass.getSimpleName().equals("QuotaCounter")
                                            || javaClass.getSimpleName().equals("MatchQueue")));

    private static final ArchCondition<JavaClass> ONLY_USED_BY_OWNER_AND_ITS_INFRA =
            new ArchCondition<>("선언 패키지와 같은 이름의 infra.<도메인>에서만 참조된다") {
                @Override
                public void check(JavaClass port, ConditionEvents events) {
                    String owner = sliceOf(port).orElseThrow();
                    for (Dependency dependency : port.getDirectDependenciesToSelf()) {
                        Optional<String> user = sliceOf(dependency.getOriginClass());
                        if (user.isEmpty()
                                || user.get().equals(owner)
                                || user.get().equals(infraOf(owner))) {
                            continue;
                        }
                        events.add(
                                SimpleConditionEvent.violated(
                                        dependency, dependency.getDescription()));
                    }
                }
            };

    // (4) 저장 포트는 선언 패키지와 같은 이름의 infra.<도메인> 밖에서 참조하지 않는다
    static final ArchRule RULE_4_STORAGE_PORTS_STAY_INSIDE =
            classes()
                    .that()
                    .resideInAnyPackage(CORE_PACKAGES)
                    .and(STORAGE_PORT)
                    .should(ONLY_USED_BY_OWNER_AND_ITS_INFRA)
                    .allowEmptyShould(true)
                    .because("Repository는 자기 패키지 밖에서 쓰지 않는다(D-32, 13.1 규칙 4)");

    private static final ArchCondition<JavaClass> NOT_USE_FOREIGN_ENTITIES =
            new ArchCondition<>("다른 도메인 패키지의 엔티티를 참조하지 않는다") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    Optional<String> user = sliceOf(javaClass);
                    if (user.isEmpty()) {
                        return;
                    }
                    for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        if (!target.isAnnotatedWith(Entity.class)) {
                            continue;
                        }
                        Optional<String> owner = sliceOf(target);
                        if (owner.isEmpty()
                                || owner.equals(user)
                                || user.get().equals(infraOf(owner.get()))) {
                            continue;
                        }
                        events.add(
                                SimpleConditionEvent.violated(
                                        dependency, dependency.getDescription()));
                    }
                }
            };

    // (5) 다른 도메인 패키지의 엔티티 클래스를 import하지 않는다(투영·조회 서비스·사건·포트만)
    static final ArchRule RULE_5_NO_FOREIGN_ENTITIES =
            classes()
                    .that()
                    .resideInAPackage(ROOT + "..")
                    .should(NOT_USE_FOREIGN_ENTITIES)
                    .allowEmptyShould(true)
                    .because("패키지 밖 엔티티 참조는 식별자로 한다(D-02, D-32, 13.1 규칙 5)");

    private static final String COMMON = ROOT + ".common..";
    private static final String DOMAIN_EVENT = ROOT + ".common.DomainEvent";
    private static final String DOMAIN_EVENT_PUBLISHER = ROOT + ".common.DomainEventPublisher";

    // (6) common에는 DomainEvent·DomainEventPublisher 말고 사건 구현·인터페이스·@Component를 두지 않는다
    static final ArchRule RULE_6_COMMON_STAYS_PLAIN =
            CompositeArchRule.of(
                            classes()
                                    .that()
                                    .resideInAPackage(COMMON)
                                    .and()
                                    .areInterfaces()
                                    .should()
                                    .haveFullyQualifiedName(DOMAIN_EVENT)
                                    .orShould()
                                    .haveFullyQualifiedName(DOMAIN_EVENT_PUBLISHER)
                                    .allowEmptyShould(true))
                    .and(
                            noClasses()
                                    .that()
                                    .resideInAPackage(COMMON)
                                    .and()
                                    .doNotHaveFullyQualifiedName(DOMAIN_EVENT)
                                    .should()
                                    .implement(DOMAIN_EVENT)
                                    .allowEmptyShould(true))
                    .and(
                            noClasses()
                                    .that()
                                    .resideInAPackage(COMMON)
                                    .should()
                                    .beMetaAnnotatedWith(Component.class)
                                    .allowEmptyShould(true))
                    .because("common은 변경이 드문 불변 기본 형식만 둔다(5.1.1, D-31, 13.1 규칙 6)");

    private static ArchRule liveMessagesStayInside(String parent) {
        String live = ROOT + "." + parent + ".live..";
        return CompositeArchRule.of(
                        classes()
                                .that()
                                .resideInAPackage(live)
                                .should()
                                .onlyDependOnClassesThat()
                                .resideInAnyPackage(
                                        ROOT + "." + parent + "..", ROOT + ".common..", "java..")
                                .allowEmptyShould(true))
                .and(
                        classes()
                                .that()
                                .resideInAPackage(live)
                                .should()
                                .onlyHaveDependentClassesThat()
                                .resideInAPackage(ROOT + "." + parent + "..")
                                .allowEmptyShould(true));
    }

    // (7) battle.live·crew.live·crew.chat.live는 자기 상위 패키지와 common만 import하고, 이들을 import하는 것도 자기 상위
    // 패키지뿐
    static final ArchRule RULE_7_LIVE_MESSAGES_STAY_INSIDE =
            CompositeArchRule.of(liveMessagesStayInside("battle"))
                    .and(liveMessagesStayInside("crew"))
                    .and(liveMessagesStayInside("crew.chat"))
                    .because("실시간 메시지는 만드는 패키지에 두고 realtime은 type()만 본다(D-06, 13.1 규칙 7)");

    /**
     * 안정성 예외 쌍 하나(D-133, D-134): from 패키지가 to 패키지에서 쓰는 형식은 allowed뿐이고, calls에 적은 형식은 적은 연산만 부른다.
     */
    private static ArchRule narrowDependency(
            String from, String to, Set<String> allowed, Map<String, Set<String>> calls) {
        Set<String> allowedNames =
                allowed.stream()
                        .map(name -> ROOT + "." + to + "." + name)
                        .collect(Collectors.toSet());
        Map<String, Set<String>> allowedCalls =
                calls.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        e -> ROOT + "." + to + "." + e.getKey(),
                                        Map.Entry::getValue));
        ArchCondition<JavaClass> onlyAllowedCalls =
                new ArchCondition<>(to + " " + calls + " 말고는 부르지 않는다") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getMethodCallsFromSelf().stream()
                                .filter(
                                        call ->
                                                allowedCalls.containsKey(
                                                        call.getTargetOwner().getName()))
                                .filter(
                                        call ->
                                                !allowedCalls
                                                        .get(call.getTargetOwner().getName())
                                                        .contains(call.getName()))
                                .forEach(
                                        call ->
                                                events.add(
                                                        SimpleConditionEvent.violated(
                                                                call, call.getDescription())));
                    }
                };
        return CompositeArchRule.of(
                        noClasses()
                                .that()
                                .resideInAPackage(ROOT + "." + from + "..")
                                .should()
                                .dependOnClassesThat(
                                        DescribedPredicate.describe(
                                                to + "의 " + allowed + " 밖 형식",
                                                (JavaClass target) ->
                                                        (target.getPackageName() + ".")
                                                                        .startsWith(
                                                                                ROOT + "." + to
                                                                                        + ".")
                                                                && !allowedNames.contains(
                                                                        outermost(target))))
                                .allowEmptyShould(true))
                .and(
                        classes()
                                .that()
                                .resideInAPackage(ROOT + "." + from + "..")
                                .should(onlyAllowedCalls)
                                .allowEmptyShould(true));
    }

    /** 중첩 형식(Side$MemberSide)은 바깥 형식 이름으로 본다. */
    private static String outermost(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    // (11) 안정성 예외 쌍의 import 범위: member → policy는 TierBoundary·OperationPolicy(tierBoundary 조회 하나)만
    // (D-133), reward → battle은 BattleJudged·BattleWin·Side·BattleResultService(winsJudgedBetween
    // 하나)만(D-134)
    static final ArchRule RULE_11_STABILITY_EXCEPTIONS_STAY_NARROW =
            CompositeArchRule.of(
                            narrowDependency(
                                    "member",
                                    "policy",
                                    Set.of("TierBoundary", "OperationPolicy"),
                                    Map.of("OperationPolicy", Set.of("tierBoundary"))))
                    .and(
                            narrowDependency(
                                    "reward",
                                    "battle",
                                    Set.of(
                                            "BattleJudged",
                                            "BattleWin",
                                            "Side",
                                            "BattleResultService"),
                                    Map.of("BattleResultService", Set.of("winsJudgedBetween"))))
                    .because(
                            "안정성 역전 두 쌍은 쓰는 형식이 좁을 때만 허용한다. 넓히려면 의존 역전을 다시 검토한다"
                                    + "(D-133, D-134, 13.1 규칙 11)");

    // (12) web.attempt(시도 제한)는 java.*만 import한다(D-132). 표현 패키지
    // 넷(web.auth·web.admin·web.chat·web.crew)만 쓴다.
    static final ArchRule RULE_12_ATTEMPT_USES_ONLY_JDK =
            classes()
                    .that()
                    .resideInAPackage(ROOT + ".web.attempt..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(ROOT + ".web.attempt..", "java..")
                    .allowEmptyShould(true)
                    .because("시도 제한은 Fan-Out 0인 하위 공용 패키지다(D-132, 13.1 규칙 12)");

    /** 조립 패키지(D-34, D-135): 도메인 서비스가 import하지 않는다. */
    static final List<String> ASSEMBLIES = List.of("operator", "report.target");

    /** crew·crew.chat의 신고용 공개 연산(D-135). report.target만 부른다. */
    static final Set<String> REPORT_ONLY_METHODS =
            Set.of("reportedView", "hideReported", "unhideReported");

    static final Set<String> REPORT_ONLY_OWNERS =
            Set.of(ROOT + ".crew.CrewPostService", ROOT + ".crew.chat.CrewChatService");

    private static final ArchCondition<JavaClass> NOT_CALL_REPORT_ONLY_METHODS =
            new ArchCondition<>("crew·crew.chat의 신고용 공개 연산을 부르지 않는다") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getMethodCallsFromSelf().stream()
                            .filter(
                                    call ->
                                            REPORT_ONLY_OWNERS.contains(
                                                    call.getTargetOwner().getName()))
                            .filter(call -> REPORT_ONLY_METHODS.contains(call.getName()))
                            .forEach(
                                    call ->
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            call, call.getDescription())));
                }
            };

    // (8) 도메인 서비스 → 조립 패키지(operator, report.target) import 0. 신고용 공개 연산은 report.target만 부른다
    static final ArchRule RULE_8_DOMAIN_DOES_NOT_KNOW_OPERATOR =
            CompositeArchRule.of(
                            noClasses()
                                    .that()
                                    .resideInAnyPackage(
                                            packagesOf(
                                                    CORE.stream()
                                                            .filter(
                                                                    name ->
                                                                            !ASSEMBLIES.contains(
                                                                                    name))
                                                            .toList()))
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage(packagesOf(ASSEMBLIES))
                                    .allowEmptyShould(true))
                    .and(
                            classes()
                                    .that()
                                    .resideOutsideOfPackage(ROOT + ".report.target..")
                                    .and()
                                    .doNotHaveFullyQualifiedName(ROOT + ".crew.CrewPostService")
                                    .and()
                                    .doNotHaveFullyQualifiedName(
                                            ROOT + ".crew.chat.CrewChatService")
                                    .should(NOT_CALL_REPORT_ONLY_METHODS)
                                    .allowEmptyShould(true))
                    .because(
                            "서비스는 확인된 운영자 계정 문자열(actorId)만 받고, 신고 대상 연결은 report.target에만 있다"
                                    + "(D-34, D-50, D-135, 13.1 규칙 8)");

    /**
     * 필드 setter: set + 같은 이름의 필드가 있는 메소드. RunningGoal.setWeeklyGoal(설계 6.1.4의 업무 동사, 필드는
     * weeklyGoals)처럼 이름만 set으로 시작하는 업무 연산은 setter가 아니다(B5 결정).
     */
    private static final DescribedPredicate<JavaMethod> SETS_A_FIELD =
            DescribedPredicate.describe(
                    "같은 이름의 필드를 바꾸는 set 메소드",
                    method -> {
                        String property = method.getName().substring("set".length());
                        String field =
                                Character.toLowerCase(property.charAt(0)) + property.substring(1);
                        return method.getOwner().getAllFields().stream()
                                .anyMatch(f -> f.getName().equals(field));
                    });

    // (9) 엔티티에 public setter가 없다
    static final ArchRule RULE_9_ENTITIES_HAVE_NO_PUBLIC_SETTERS =
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .areAnnotatedWith(Entity.class)
                    .and()
                    .haveNameMatching("set[A-Z].*")
                    .and(SETS_A_FIELD)
                    .should()
                    .bePublic()
                    .allowEmptyShould(true)
                    .because("상태 변경은 업무 동사 메소드로만 한다(13.1 규칙 9)");

    private static final DescribedPredicate<JavaClass> ENTITY_OR_VALUE_OBJECT =
            DescribedPredicate.describe(
                    "엔티티·값 객체(@Entity, @Embeddable, record)",
                    javaClass ->
                            javaClass.isAnnotatedWith(Entity.class)
                                    || javaClass.isAnnotatedWith(Embeddable.class)
                                    || javaClass
                                            .getRawSuperclass()
                                            .map(s -> s.getName().equals("java.lang.Record"))
                                            .orElse(false));

    // (10) 엔티티·값 객체에 public 필드가 없다(static 상수 제외)
    static final ArchRule RULE_10_NO_PUBLIC_FIELDS =
            noFields()
                    .that()
                    .areDeclaredInClassesThat(ENTITY_OR_VALUE_OBJECT)
                    .and()
                    .areNotStatic()
                    .should()
                    .bePublic()
                    .allowEmptyShould(true)
                    .because("엔티티 속성은 모두 private이다(13.1 규칙 10)");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(ROOT);
    }

    @Test
    void designPackageList_has77NamesAndEachHasAnAllowList() {
        assertThat(Set.copyOf(PACKAGES)).hasSize(77);
        assertThat(ALLOWED.keySet()).containsExactlyInAnyOrderElementsOf(PACKAGES);
    }

    /** B5 완료 조건: 5.1.1의 70개 패키지마다 서버 클래스가 하나 이상 있다(빈 패키지 0). */
    @Test
    void everyDesignPackage_hasMainClasses() {
        Set<String> filled =
                classes.stream()
                        .map(ArchitectureTest::sliceOf)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet());
        assertThat(PACKAGES).as("클래스가 없는 5.1.1 패키지").allMatch(filled::contains);
    }

    @Test
    void mainClasses_resideInDesignPackages() {
        List<String> stray =
                classes.stream().filter(c -> sliceOf(c).isEmpty()).map(JavaClass::getName).toList();
        assertThat(stray).as("5.1.1 패키지 70개 밖의 서버 클래스").isEmpty();
    }

    @Test
    void rule01_onlyDependenciesListedIn12_2() {
        RULE_1_ONLY_LISTED_DEPENDENCIES.check(classes);
    }

    @Test
    void rule02_noCycles() {
        RULE_2_NO_CYCLES.check(classes);
    }

    @Test
    void rule03_coreDoesNotImportWebRealtimeBatchInfra() {
        RULE_3_CORE_DOES_NOT_KNOW_OUTER_LAYERS.check(classes);
    }

    @Test
    void rule04_storagePortsUsedOnlyByOwnerAndItsInfra() {
        RULE_4_STORAGE_PORTS_STAY_INSIDE.check(classes);
    }

    @Test
    void rule05_noForeignEntities() {
        RULE_5_NO_FOREIGN_ENTITIES.check(classes);
    }

    @Test
    void rule06_commonHoldsOnlyPlainTypes() {
        RULE_6_COMMON_STAYS_PLAIN.check(classes);
    }

    @Test
    void rule07_liveMessagesStayInParent() {
        RULE_7_LIVE_MESSAGES_STAY_INSIDE.check(classes);
    }

    @Test
    void rule08_domainDoesNotImportOperator() {
        RULE_8_DOMAIN_DOES_NOT_KNOW_OPERATOR.check(classes);
    }

    @Test
    void rule11_stabilityExceptionPairsUseOnlyListedTypes() {
        RULE_11_STABILITY_EXCEPTIONS_STAY_NARROW.check(classes);
    }

    @Test
    void rule12_attemptLimiterUsesOnlyJdk() {
        RULE_12_ATTEMPT_USES_ONLY_JDK.check(classes);
    }

    @Test
    void rule09_entitiesHaveNoPublicSetters() {
        RULE_9_ENTITIES_HAVE_NO_PUBLIC_SETTERS.check(classes);
    }

    @Test
    void rule10_entitiesAndValueObjectsHaveNoPublicFields() {
        RULE_10_NO_PUBLIC_FIELDS.check(classes);
    }

    /** 13.1 서비스 의존: 생성자 주입만(필드 주입 금지). 설계 규칙 10개 밖의 공통 코딩 규칙. */
    @Test
    void generalCodingRules() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(classes);
    }
}
