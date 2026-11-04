package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.member.PlacementProgress;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.running.ValidityRule;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 앱 계약 자료 동기화 시험(재발 방지). 앱 android/contract/sync_from_server.py가 서버 소스에서 뽑는 값 계약 — enum 상수, 오류 응답
 * code, web 요청·응답 record(항목 이름·필수 여부·별칭·중첩 형식), 유효 판정 초과 시속 상한(policy/ValidityRule.txt), 첫 티어 배치
 * 횟수(policy/TierPlacement.txt), 닫힌 기능 알림 분류(policy/GatedNotification.txt) — 을 서버에서 반사(reflection)로
 * 같은 형식으로 만들어 src/test/resources/contract/app/의 기준 파일과 비교한다. 서버 계약이 바뀌면 이 시험이 실패하고, 기준 파일을 갱신하면 그
 * 변경분이 곧 앱 작업자에게 넘길 계약 변경 목록이다.
 */
@DisplayName("앱 계약 자료(enum·오류 code·web record) 기준 파일과 서버가 같다")
class AppContractSnapshotTest {

    static final String UPDATE_ENV = "RUNKK_CONTRACT_UPDATE";
    private static final Path BASELINE = Path.of("src", "test", "resources", "contract", "app");
    private static final Path HANDLER =
            Path.of(
                    "src",
                    "main",
                    "java",
                    "com",
                    "a207",
                    "runkk",
                    "web",
                    "error",
                    "ApiExceptionHandler.java");
    private static final String ROOT = "com.a207.runkk";
    private static final String HOW_TO_UPDATE =
            "\n"
                + "서버 계약(enum·오류 code·web 요청/응답 record)이 기준 파일과 다르다. 의도한 변경이면 기준 파일을 갱신하고 그 diff를 앱"
                + " 작업자에게 넘긴다:\n"
                + "  cd backend && "
                    + UPDATE_ENV
                    + "=true ./gradlew test --tests com.a207.runkk.AppContractSnapshotTest\n"
                    + "  (Windows PowerShell: $env:"
                    + UPDATE_ENV
                    + "='true'; ./gradlew test --tests com.a207.runkk.AppContractSnapshotTest)\n"
                    + "그 뒤 android/contract/는 python android/contract/sync_from_server.py로 같은 내용을"
                    + " 만든다.\n";

    /** sync_from_server.py ENUMS와 같은 목록(패키지 → enum). */
    private static final Map<String, List<String>> ENUMS = new LinkedHashMap<>();

    static {
        ENUMS.put(
                "common",
                List.of(
                        "PersonalityTag",
                        "ReasonCode",
                        "RecordVisibility",
                        "StandardDistance",
                        "ItemSlot",
                        "Tier",
                        "AggregationPeriod",
                        "DistanceUnit"));
        ENUMS.put(
                "member",
                List.of(
                        "ConsentItem",
                        "FreeRunTarget",
                        "GapAlertInterval",
                        "ProfileVisibility",
                        "Gender",
                        "ProviderType",
                        "SettingItem",
                        "DocumentType",
                        "AccountType",
                        "ScreenTheme",
                        "PlacementStatus"));
        ENUMS.put("notification", List.of("NotificationType", "Screen"));
        ENUMS.put("avatar", List.of("DefaultCharacter", "BodyShape", "SkinTone", "FailureReason"));
        ENUMS.put("battle", List.of("BattleType", "Verdict", "PlacementRoundState"));
        ENUMS.put(
                "running",
                List.of("CardKind", "RunMode", "SignalState", "DisposalKind", "StartDevice"));
        ENUMS.put(
                "crew",
                List.of(
                        "CrewVisibility",
                        "JoinPolicy",
                        "AttendanceAnswer",
                        "PostKind",
                        "CrewRole",
                        "CrewRunStatus",
                        "Readiness",
                        "JoinRequestStatus",
                        "PollMethod"));
        ENUMS.put("crew.chat", List.of("ReactionKind"));
        ENUMS.put("crew.chat.live", List.of("ChatRemovalReason"));
        ENUMS.put("report", List.of("ReportTargetKind", "ReportReason", "ReportStatus"));
    }

    /** sync_from_server.py가 web 밖에서 더 따라가는 record. */
    private static final List<String> EXTRA_ROOTS =
            List.of(
                    "InboxView",
                    "InboxView.Item",
                    "NavigationTarget",
                    "MemberSettingsView",
                    "RunSettings",
                    "BattleSettings",
                    "LegalDocument",
                    "DisposalOrderView",
                    "WithdrawalResult");

    private static final Set<String> LIST_TYPES =
            Set.of(
                    List.class.getName(),
                    Set.class.getName(),
                    java.util.Collection.class.getName(),
                    Optional.class.getName(),
                    Iterable.class.getName());

    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    @DisplayName(
            "TC-5703·TC-5704·TC-5706·TC-5707 서버 계약(배치고사 enum·응답 record 포함)이 바뀌면 실패하고 갱신 방법을 알린다")
    void serverContract_matchesBaseline() throws Exception {
        Map<String, String> generated = generate();
        if (Boolean.parseBoolean(System.getenv(UPDATE_ENV))) {
            write(generated);
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : generated.entrySet()) {
            Path file = BASELINE.resolve(e.getKey());
            if (!Files.exists(file)) {
                problems.add(e.getKey() + ": 기준 파일 없음");
                continue;
            }
            String expected = Files.readString(file, StandardCharsets.UTF_8);
            if (!same(e.getKey(), expected, e.getValue())) {
                problems.add(e.getKey() + ": " + difference(e.getKey(), expected, e.getValue()));
            }
        }
        try (var files = Files.walk(BASELINE)) {
            files.filter(Files::isRegularFile)
                    .map(p -> BASELINE.relativize(p).toString().replace('\\', '/'))
                    .filter(name -> !generated.containsKey(name))
                    .forEach(name -> problems.add(name + ": 서버에 더는 없는 기준 파일"));
        }
        assertThat(problems).as(HOW_TO_UPDATE).isEmpty();
    }

    private static boolean same(String name, String expected, String actual) throws IOException {
        if (name.endsWith(".json")) {
            return JSON.readTree(expected).equals(JSON.readTree(actual));
        }
        return lines(expected).equals(lines(actual));
    }

    private static List<String> lines(String text) {
        return text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    private static String difference(String name, String expected, String actual)
            throws IOException {
        if (!name.endsWith(".json")) {
            Set<String> before = new TreeSet<>(lines(expected));
            Set<String> after = new TreeSet<>(lines(actual));
            Set<String> added = new TreeSet<>(after);
            added.removeAll(before);
            Set<String> removed = new TreeSet<>(before);
            removed.removeAll(after);
            return "추가 "
                    + added
                    + ", 삭제 "
                    + removed
                    + (added.isEmpty() && removed.isEmpty() ? " (순서 변경)" : "");
        }
        JsonNode before = JSON.readTree(expected);
        JsonNode after = JSON.readTree(actual);
        List<String> changes = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        before.fieldNames().forEachRemaining(keys::add);
        after.fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            if (!before.has(key)) {
                changes.add("+" + key);
            } else if (!after.has(key)) {
                changes.add("-" + key);
            } else if (!before.get(key).equals(after.get(key))) {
                changes.add("~" + key);
            }
        }
        return "record 변경 " + changes;
    }

    private static void write(Map<String, String> generated) throws IOException {
        if (Files.exists(BASELINE)) {
            try (var files = Files.walk(BASELINE)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    Files.delete(p);
                }
            }
        }
        for (Map.Entry<String, String> e : generated.entrySet()) {
            Path file = BASELINE.resolve(e.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
        }
    }

    // ---------- 만들기 ----------

    static Map<String, String> generate() throws Exception {
        Map<String, String> files = new TreeMap<>();
        enums(files);
        files.put("dto-records.json", JSON.writeValueAsString(records()) + "\n");
        // 앱 달리는 중 거리가 같은 구간을 빼도록 초과 시속 상한을 넘긴다(FR-047 v0.10, 앱 OverspeedRule)
        files.put(
                "policy/ValidityRule.txt",
                text(List.of("maxSpeedKmh " + ValidityRule.STANDARD.maxSpeedKmh())));
        // 앱 배치고사 진행 「n/3」의 분모(RandomBattleGate.PLACEMENT_RUNS)가 서버 판 수와 같도록 넘긴다(H-44, 05-21 D-509)
        files.put(
                "policy/TierPlacement.txt",
                text(List.of("placementRuns " + PlacementProgress.ROUNDS)));
        // 앱 GatedFeature(알림함·시스템 푸시 숨김)가 서버 푸시 차단(D138)과 같은 종류 집합을 쓰도록 넘긴다(H-47). 딸린 기능이 없으면 "-"
        files.put("policy/GatedNotification.txt", text(gatedNotifications()));
        return files;
    }

    /** "알림종류 미공개기능" 줄(NotificationType 순서). 딸린 기능이 없으면 "-". */
    private static List<String> gatedNotifications() {
        return Arrays.stream(NotificationType.values())
                .map(
                        t ->
                                t.name()
                                        + " "
                                        + (t.gatedFeature() == null
                                                ? "-"
                                                : t.gatedFeature().name()))
                .toList();
    }

    private static void enums(Map<String, String> files) throws Exception {
        for (Map.Entry<String, List<String>> e : ENUMS.entrySet()) {
            for (String name : e.getValue()) {
                Class<?> type = Class.forName(ROOT + "." + e.getKey() + "." + name);
                Object[] constants = type.getEnumConstants();
                files.put(
                        "enums/" + name + ".txt",
                        text(Arrays.stream(constants).map(c -> ((Enum<?>) c).name()).toList()));
                if (name.equals("ConsentItem")) {
                    List<String> required = new ArrayList<>();
                    for (Object c : constants) {
                        if ((boolean) type.getMethod("isRequired").invoke(c)) {
                            required.add(((Enum<?>) c).name());
                        }
                    }
                    files.put("enums/ConsentItem.required.txt", text(required));
                }
                if (name.equals("StandardDistance")) {
                    files.put("enums/StandardDistance.txt", text(standardDistances(constants)));
                }
                if (name.equals("PersonalityTag")) {
                    List<String> rows = new ArrayList<>();
                    for (Object c : constants) {
                        rows.add(
                                ((Enum<?>) c).name()
                                        + " "
                                        + ((Enum<?>) type.getMethod("category").invoke(c)).name());
                    }
                    files.put("enums/PersonalityTag.categories.txt", text(rows));
                }
            }
        }
        String handler = Files.readString(HANDLER, StandardCharsets.UTF_8);
        Set<String> codes = new TreeSet<>();
        Matcher m = Pattern.compile("new ErrorResponse\\(\"([A-Z_]+)\"").matcher(handler);
        while (m.find()) {
            codes.add(m.group(1));
        }
        files.put("enums/ErrorCode.http.txt", text(List.copyOf(codes)));
    }

    private static List<String> standardDistances(Object[] constants) throws Exception {
        List<String> rows = new ArrayList<>();
        String[][] uses = {
            {"isRandomBattle", "RANDOM_BATTLE"},
            {"isSelfBattle", "SELF_BATTLE"},
            {"isCrewRun", "CREW_RUN"},
            {"isRecordBase", "RECORD_BASE"}
        };
        for (Object c : constants) {
            Class<?> type = c.getClass();
            Object distance = type.getMethod("distance").invoke(c);
            long meters = (long) distance.getClass().getMethod("meters").invoke(distance);
            List<String> names = new ArrayList<>();
            for (String[] use : uses) {
                if ((boolean) type.getMethod(use[0]).invoke(c)) {
                    names.add(use[1]);
                }
            }
            rows.add(
                    ((Enum<?>) c).name()
                            + " "
                            + meters
                            + " "
                            + (names.isEmpty() ? "-" : String.join(",", names)));
        }
        return rows;
    }

    private static String text(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }

    /** web 패키지의 record와 거기서 닿는 record. 키는 바깥 클래스부터 이은 단순 이름(Outer.Inner). */
    private static ObjectNode records() {
        Map<Class<?>, String> keys = new LinkedHashMap<>();
        for (JavaClass javaClass :
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(ROOT)) {
            Class<?> type = javaClass.reflect();
            if (type.isRecord() && !type.isAnonymousClass()) {
                keys.put(type, keyOf(type));
            }
        }
        Map<String, Class<?>> byKey = new TreeMap<>();
        keys.forEach((type, key) -> byKey.put(key, type));
        Deque<Class<?>> stack = new ArrayDeque<>();
        keys.keySet().stream()
                .filter(t -> t.getPackageName().startsWith(ROOT + ".web"))
                .forEach(stack::push);
        EXTRA_ROOTS.stream().map(byKey::get).filter(t -> t != null).forEach(stack::push);
        Map<String, Class<?>> keep = new TreeMap<>();
        while (!stack.isEmpty()) {
            Class<?> type = stack.pop();
            if (keep.putIfAbsent(keys.get(type), type) != null) {
                continue;
            }
            for (RecordComponent c : type.getRecordComponents()) {
                refOf(c.getGenericType(), keys).ifPresent(stack::push);
            }
        }
        ObjectNode out = JSON.createObjectNode();
        keep.forEach((key, type) -> out.set(key, recordNode(type, keys)));
        return out;
    }

    private static String keyOf(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
            names.add(0, c.getSimpleName());
        }
        return String.join(".", names);
    }

    private static Class<?> topLevel(Class<?> type) {
        Class<?> c = type;
        while (c.getEnclosingClass() != null) {
            c = c.getEnclosingClass();
        }
        return c;
    }

    private static Optional<Class<?>> refOf(Type type, Map<Class<?>, String> keys) {
        Type inner = type;
        if (type instanceof ParameterizedType p) {
            String raw = ((Class<?>) p.getRawType()).getName();
            if (!LIST_TYPES.contains(raw)) {
                return Optional.empty();
            }
            inner = p.getActualTypeArguments()[0];
        }
        return inner instanceof Class<?> c && keys.containsKey(c)
                ? Optional.of(c)
                : Optional.empty();
    }

    private static ObjectNode recordNode(Class<?> type, Map<Class<?>, String> keys) {
        ObjectNode node = JSON.createObjectNode();
        ArrayNode components = node.putArray("components");
        for (RecordComponent c : type.getRecordComponents()) {
            ObjectNode comp = components.addObject();
            comp.put("name", c.getName());
            Field field = fieldOf(type, c.getName());
            if (field.isAnnotationPresent(NotNull.class)
                    || field.isAnnotationPresent(NotBlank.class)
                    || field.isAnnotationPresent(NotEmpty.class)) {
                comp.put("required", true);
            }
            JsonAlias alias = field.getAnnotation(JsonAlias.class);
            if (alias != null && alias.value().length > 0) {
                ArrayNode aliases = comp.putArray("aliases");
                Arrays.stream(alias.value()).forEach(aliases::add);
            }
            Type generic = c.getGenericType();
            boolean list =
                    generic instanceof ParameterizedType p
                            && LIST_TYPES.contains(((Class<?>) p.getRawType()).getName());
            boolean map =
                    generic instanceof ParameterizedType p
                            && Map.class.isAssignableFrom((Class<?>) p.getRawType());
            Optional<Class<?>> ref = refOf(generic, keys);
            if (map) {
                comp.put("kind", "map");
            } else if (list) {
                comp.put("kind", ref.isPresent() ? "list" : "list-leaf");
            } else {
                comp.put("kind", ref.isPresent() ? "record" : "leaf");
            }
            ref.ifPresent(r -> comp.put("ref", keys.get(r)));
        }
        Class<?> top = topLevel(type);
        node.put(
                "file",
                top.getPackageName().substring(ROOT.length() + 1).replace('.', '/')
                        + "/"
                        + top.getSimpleName()
                        + ".java");
        return node;
    }

    private static Field fieldOf(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
