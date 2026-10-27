package com.a207.runkk.web.admin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.PageCursor;
import com.a207.runkk.report.ReportCaseListView;
import com.a207.runkk.report.ReportService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 관리툴 신고 목록 크기(PageCursor 규칙): size가 없으면 30, 1~100만 받는다. 요청 단위 400은 ReportControllerTest가 본다. */
@DisplayName("web.admin.report 신고 목록 크기")
class ReportAdminPagingTest {

    private final ReportService service = mock(ReportService.class);
    private final ReportAdminController controller = new ReportAdminController(service);

    private PageCursor requested(Long cursor, Integer size) {
        when(service.cases(any(), any())).thenReturn(new ReportCaseListView(List.of(), null));
        controller.cases(null, cursor, size);
        ArgumentCaptor<PageCursor> captor = ArgumentCaptor.forClass(PageCursor.class);
        verify(service).cases(any(Optional.class), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("size를 주지 않으면 기본 30개씩 읽는다(전부 읽지 않는다)")
    void defaultSize_is30() {
        assertThat(requested(null, null)).isEqualTo(new PageCursor(null, 30));
        assertThat(ReportAdminController.DEFAULT_SIZE).isEqualTo(30);
    }

    @Test
    @DisplayName("size를 주면 그 크기, 상한 100")
    void givenSize_isKept() {
        assertThat(requested(7L, PageCursor.MAX_SIZE)).isEqualTo(new PageCursor(7L, 100));
    }

    @Test
    @DisplayName("size 101·0은 거부한다(400으로 바뀐다)")
    void outOfRange_isRejected() {
        assertThatThrownBy(() -> controller.cases(null, null, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.cases(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
