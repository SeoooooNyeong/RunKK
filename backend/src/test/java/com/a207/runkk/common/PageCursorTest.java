package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PageCursor 커서 페이지")
class PageCursorTest {

    private static final List<Long> IDS = List.of(9L, 7L, 5L, 3L, 1L);

    @Test
    @DisplayName("size가 없으면 전부, 있으면 size개와 다음 커서, 마지막 페이지는 커서 없음")
    void slices() {
        assertThat(PageCursor.all().slice(IDS, Long::longValue).items()).isEqualTo(IDS);
        assertThat(PageCursor.all().slice(IDS, Long::longValue).nextCursor()).isNull();
        PageCursor.Slice<Long> first = new PageCursor(null, 2).slice(IDS, Long::longValue);
        assertThat(first.items()).containsExactly(9L, 7L);
        assertThat(first.nextCursor()).isEqualTo(7L);
        PageCursor.Slice<Long> second =
                new PageCursor(first.nextCursor(), 2).slice(IDS, Long::longValue);
        assertThat(second.items()).containsExactly(5L, 3L);
        PageCursor.Slice<Long> last =
                new PageCursor(second.nextCursor(), 2).slice(IDS, Long::longValue);
        assertThat(last.items()).containsExactly(1L);
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서 항목이 지워졌으면 그보다 작은 ID부터, 크기 범위 밖은 거부")
    void deletedCursor_andBounds() {
        assertThat(new PageCursor(6L, 10).slice(IDS, Long::longValue).items())
                .containsExactly(5L, 3L, 1L);
        assertThatThrownBy(() -> new PageCursor(null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageCursor(null, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageCursor(0L, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
