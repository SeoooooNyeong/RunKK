package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkDdayBadge
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPageDots
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatusChip
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.RunkkUnderlineTabs
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.notification.Event
import java.time.LocalDate

/** 이벤트 목록(피그마 I-3b 62271:6986, Dark 62274:6818). 카드나 배너를 누르면 상세 웹 페이지를 연다. */
@Composable
fun EventListRoute(viewModel: EventListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    // 상세에서 셀럽런, 자유런으로 갔다 돌아오면 참여 중 칩과 응모 수를 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    EventListScreen(
        state = state,
        onBack = navigator::back,
        onTab = viewModel::onTab,
        onEvent = { event -> eventDetailKey(event)?.let(navigator::navigate) },
        onRetry = viewModel::refresh,
        loadImage = viewModel::image
    )
}

@Composable
fun EventListScreen(
    state: EventListUiState,
    onBack: () -> Unit,
    onTab: (EventTab) -> Unit,
    onEvent: (Event) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadImage: suspend (String) -> Outcome<ByteArray> = { Outcome.Failure(AppError.Network) }
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.event_list_title), onBack = onBack)
        EventTabs(state.tab, onTab)
        when {
            state.isLoading && state.events.isEmpty() -> RunkkLoading(modifier = Modifier.weight(1f))
            state.error != null && state.events.isEmpty() -> RunkkErrorState(state.error.asString(), onRetry = onRetry)
            else -> EventList(state, onEvent, loadImage, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EventList(
    state: EventListUiState,
    onEvent: (Event) -> Unit,
    loadImage: suspend (String) -> Outcome<ByteArray>,
    modifier: Modifier = Modifier
) {
    val visible = state.visible
    val banners = state.banners
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (banners.isNotEmpty()) {
            item(key = "banner") { BannerPager(banners, state.today, onEvent, loadImage) }
        }
        if (visible.isEmpty()) {
            item(key = "empty") { RunkkEmptyState(stringResource(state.tab.emptyRes), Modifier.padding(top = 24.dp)) }
        } else {
            item(key = "header") { ListHeader(state.tab, visible.size) }
            itemsIndexed(visible, key = { _, event -> event.id }) { index, event ->
                // 피그마 목록: 위 4, 카드 사이 12, 아래 16(LazyColumn contentPadding)
                EventCard(
                    event,
                    state.today,
                    onClick = { onEvent(event) },
                    loadImage = loadImage,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (index == 0) 4.dp else 12.dp)
                )
            }
        }
    }
}

/** 진행 중 | 지난 이벤트 밑줄 탭(디자인 시스템 RunkkUnderlineTabs). */
@Composable
private fun EventTabs(selected: EventTab, onTab: (EventTab) -> Unit) {
    RunkkUnderlineTabs(
        options = EventTab.entries.map { stringResource(it.labelRes) },
        selectedIndex = selected.ordinal,
        onSelect = { onTab(EventTab.entries[it]) }
    )
}

/** 맨 위 큰 배너(진행 중 이벤트의 배너 그림). 여러 개면 옆으로 넘기고 아래에 순서 점을 둔다. */
@Composable
private fun BannerPager(
    banners: List<Event>,
    today: LocalDate?,
    onEvent: (Event) -> Unit,
    loadImage: suspend (String) -> Outcome<ByteArray>
) {
    val pager = rememberPagerState { banners.size }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
            key = { banners[it].id }
        ) { page ->
            Banner(banners[page], today, onClick = { onEvent(banners[page]) }, loadImage = loadImage)
        }
        if (banners.size > 1) {
            RunkkPageDots(
                current = pager.currentPage,
                count = banners.size,
                description = stringResource(R.string.event_banner_page, pager.currentPage + 1, banners.size)
            )
        }
    }
}

/**
 * 배너 한 장(피그마 RunKK / 이벤트 / 배너 62269:2783): 서버 배너 그림 위 왼쪽에 D-day 배지, 제목, 부제.
 * 그림을 받기 전에는 보라 바탕이다.
 */
@Composable
private fun Banner(
    event: Event,
    today: LocalDate?,
    onClick: () -> Unit,
    loadImage: suspend (String) -> Outcome<ByteArray>
) {
    val resources = LocalContext.current.resources
    val daysLeft = eventDaysLeft(event, today)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Violet)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        EventImage(event.bannerImageUrl, loadImage, EVENT_BANNER_PX)
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 22.dp).width(BANNER_TEXT_WIDTH),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (daysLeft != null) RunkkDdayBadge(eventDdayLabel(resources, daysLeft))
            Text(
                event.title,
                style = RunkkTextStyles.text(30, FontWeight.Bold, lineHeight = 35),
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            event.subtitle?.let {
                Text(it, style = RunkkTextStyles.text(14, FontWeight.Medium), color = Color.White, maxLines = 2)
            }
        }
    }
}

/** 목록 머리: "진행 중인 이벤트 3". */
@Composable
private fun ListHeader(tab: EventTab, count: Int) {
    RunkkSectionHeader(
        stringResource(tab.headerRes),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        count = count.toString(),
        size = RunkkSectionHeaderSize.Large,
        countColor = RunkkColors.Lavender
    )
}

/**
 * 이벤트 카드(피그마 RunKK / 이벤트 / 카드 62269:2812): 썸네일, 상태 칩, 제목, 기간과 D-day, 참여 중이면 응모 수 막대.
 */
@Composable
private fun EventCard(
    event: Event,
    today: LocalDate?,
    onClick: () -> Unit,
    loadImage: suspend (String) -> Outcome<ByteArray>,
    modifier: Modifier = Modifier
) {
    val resources = LocalContext.current.resources
    val daysLeft = eventDaysLeft(event, today)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(84.dp).clip(RunkkShapes.Medium).background(RunkkColors.Violet)) {
            EventImage(event.thumbnailImageUrl, loadImage, EVENT_THUMB_PX)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EventChip.of(event).let { RunkkStatusChip(stringResource(it.labelRes), it.tone) }
            Text(
                event.title,
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eventPeriod(resources, event),
                    style = RunkkTextStyles.text(12, FontWeight.Medium),
                    color = RunkkColors.TextSecondary
                )
                if (daysLeft != null) RunkkDdayBadge(eventDdayLabel(resources, daysLeft))
            }
            if (event.isEntered && event.maxEntryCount > 0) EntryProgress(event.entryCount, event.maxEntryCount)
        }
    }
}

/** 응모 수 막대와 "1 / 2". */
@Composable
private fun EntryProgress(count: Int, max: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RunkkProgressBar(
            progress = count.toFloat() / max,
            modifier = Modifier.weight(1f),
            height = 6.dp,
            color = RunkkColors.Lavender,
            trackColor = RunkkColors.Border
        )
        Text(
            stringResource(R.string.event_entry_progress, count, max),
            style = RunkkTextStyles.text(12, FontWeight.Bold),
            color = RunkkColors.Lavender
        )
    }
}

/** 배너 글자 폭(피그마 170dp, 오른쪽은 그림 자리). */
private val BANNER_TEXT_WIDTH = 170.dp
