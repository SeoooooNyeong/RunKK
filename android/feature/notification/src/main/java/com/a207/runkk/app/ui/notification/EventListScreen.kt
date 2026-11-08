package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.notification.Event
import java.time.LocalDate

/** 이벤트 목록(피그마 이벤트 모아보기 62214:1243, Dark 62218:1243). 카드나 배너를 누르면 상세 웹 페이지를 연다. */
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
            items(visible, key = { it.id }) { event ->
                EventCard(
                    event,
                    state.today,
                    onClick = { onEvent(event) },
                    loadImage = loadImage,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            }
        }
    }
}

/** 진행 중 | 지난 이벤트 밑줄 탭. 고른 탭은 굵은 글자와 3dp 밑줄이다. */
@Composable
private fun EventTabs(selected: EventTab, onTab: (EventTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        EventTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onTab(tab) })
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(tab.labelRes),
                    style = RunkkTextStyles.text(15, if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = if (isSelected) RunkkColors.TextPrimary else RunkkColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (isSelected) 3.dp else 1.dp)
                        .background(if (isSelected) RunkkColors.TextPrimary else RunkkColors.Border)
                )
            }
        }
    }
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
        if (banners.size > 1) BannerDots(pager.currentPage, banners.size)
    }
}

/** 배너 한 장: 서버 배너 그림 위 왼쪽에 D-day 배지, 제목, 부제. 그림을 받기 전에는 보라 바탕이다. */
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
            .clip(RoundedCornerShape(24.dp))
            .background(RunkkColors.Violet)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        EventImage(event.bannerImageUrl, loadImage, EVENT_BANNER_PX)
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 22.dp).width(BANNER_TEXT_WIDTH),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (daysLeft != null) {
                Text(
                    eventDdayLabel(resources, daysLeft),
                    style = RunkkTextStyles.text(12, FontWeight.Bold),
                    color = RunkkColors.OnLimeFill,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RunkkColors.LimeFill)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
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

/** 배너 순서 점: 지금 장은 18×6 막대, 나머지는 6dp 점. 보조 기술에는 "1 / 3"으로 읽힌다. */
@Composable
private fun BannerDots(current: Int, count: Int) {
    val description = stringResource(R.string.event_banner_page, current + 1, count)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(width = if (index == current) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) RunkkColors.TextPrimary else RunkkColors.Border)
            )
        }
    }
}

/** 목록 머리: "진행 중인 이벤트 3". */
@Composable
private fun ListHeader(tab: EventTab, count: Int) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics(
            mergeDescendants = true
        ) { heading() },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(tab.headerRes),
            style = RunkkTextStyles.text(17, FontWeight.Bold),
            color = RunkkColors.TextPrimary
        )
        Text(count.toString(), style = RunkkTextStyles.text(17, FontWeight.Bold), color = RunkkColors.Lavender)
    }
}

/** 이벤트 카드: 썸네일, 상태 칩, 제목, 기간과 D-day, 참여 중이면 응모 수 막대. */
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
            .clip(RoundedCornerShape(20.dp))
            .background(RunkkColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(RunkkColors.Violet)) {
            EventImage(event.thumbnailImageUrl, loadImage, EVENT_THUMB_PX)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(EventChip.of(event))
            Text(
                event.title,
                style = RunkkTextStyles.text(16, FontWeight.Bold),
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
                if (daysLeft != null) {
                    Text(
                        eventDdayLabel(resources, daysLeft),
                        style = RunkkTextStyles.text(11, FontWeight.Bold),
                        color = RunkkColors.Surface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RunkkColors.TextPrimary)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
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

/** 상태 칩(새 이벤트 보라, 참여 중 라임, 곧 열려요와 종료 회색). */
@Composable
private fun StatusChip(chip: EventChip) {
    val (background, content) = when (chip) {
        EventChip.NEW -> RunkkColors.Lavender.copy(alpha = NEW_CHIP_ALPHA) to RunkkColors.Lavender
        EventChip.ENTERED -> RunkkColors.LimeFill to RunkkColors.OnLimeFill
        EventChip.UPCOMING, EventChip.ENDED -> RunkkColors.Border to RunkkColors.TextSecondary
    }
    Text(
        stringResource(chip.labelRes),
        style = RunkkTextStyles.text(11, FontWeight.Bold),
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** 배너 글자 폭(피그마 170dp, 오른쪽은 그림 자리). */
private val BANNER_TEXT_WIDTH = 170.dp

/** 새 이벤트 칩 바탕: 보라 글자색을 옅게(피그마 라이트 #E3DFF9). */
private const val NEW_CHIP_ALPHA = 0.16f
