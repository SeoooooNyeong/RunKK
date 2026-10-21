package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.policy.RunimalRule;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 1단계 코드 리뷰 결과서(review-backend.md) 결함 중 행동으로 드러나지 않는 구조 결함을 고정한다. 결함 ID는 B2 보고서의 M·L 번호다(중 M1~M6, 경
 * L1~L14).
 */
@DisplayName("1단계 리뷰 구조 결함 회귀")
class ReviewFindingsTest {

    private static final Path MAIN = Path.of("src/main/java/com/a207/runkk");

    private static List<String> lines(String file) throws IOException {
        return Files.readAllLines(MAIN.resolve(file));
    }

    @Test
    @DisplayName("M1: StandardDistance 생성자에 위치로만 읽히는 boolean 매개변수가 없다")
    void standardDistance_constructorHasNoBooleanFlags() {
        for (Constructor<?> c : StandardDistance.class.getDeclaredConstructors()) {
            assertThat(c.getParameterTypes()).doesNotContain(boolean.class);
        }
    }

    @Test
    @DisplayName("M3: OperationSettingHistory 생성자는 매개변수 4개 이하다")
    void operationSettingHistory_constructorHasAtMostFourParameters() throws Exception {
        Class<?> type = Class.forName("com.a207.runkk.policy.OperationSettingHistory");
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            assertThat(c.getParameterCount()).isLessThanOrEqualTo(4);
        }
    }

    @Test
    @DisplayName("M4: SettingKey의 메소드는 50줄 이하다")
    void settingKey_methodsAreAtMostFiftyLines() throws IOException {
        List<String> source = lines("policy/SettingKey.java");
        int start = -1;
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).matches("\\s*private static \\S+ defaultLandmarks\\(\\).*")) {
                start = i;
            }
        }
        if (start < 0) {
            return;
        }
        int depth = 0;
        int end = start;
        for (int i = start; i < source.size(); i++) {
            depth += count(source.get(i), '{') - count(source.get(i), '}');
            if (depth == 0 && i > start) {
                end = i;
                break;
            }
        }
        assertThat(end - start + 1).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("M5·L1: SettingKey에 한 글자 변수와 검사 숫자 리터럴이 없다")
    void settingKey_hasNoSingleLetterVariablesOrMagicRangeNumbers() throws IOException {
        List<String> source = lines("policy/SettingKey.java");
        assertThat(source).noneMatch(l -> l.matches(".*\\b[A-Z]\\w* v = \\(.*"));
        assertThat(source).noneMatch(l -> l.matches(".*between\\([^)]*,\\s*\\d+,\\s*\\d+\\).*"));
        assertThat(source)
                .noneMatch(
                        l -> l.matches(".*(>=|<=|>|<)\\s*([1-9]\\d*|\\d+\\.\\d+)\\s*(&&|,|\\)).*"));
    }

    @Test
    @DisplayName("L13: RunimalRule에 TagCategory 7개 중 4개만 받는 threshold가 없다")
    void runimalRule_hasNoPartialThresholdSwitch() {
        assertThat(Arrays.stream(RunimalRule.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("threshold");
    }

    @Test
    @DisplayName("L14: OperationPolicy는 기준값을 다운캐스트하지 않는다")
    void operationPolicy_hasNoDowncasts() throws IOException {
        assertThat(lines("policy/OperationPolicy.java"))
                .noneMatch(l -> l.matches(".*\\(\\(?[A-Z]\\w*(<[^>]*>)?\\) valueOf\\(.*"));
    }

    private static int count(String line, char c) {
        return (int) line.chars().filter(ch -> ch == c).count();
    }
}
