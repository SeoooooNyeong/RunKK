package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.ResultStage
import com.a207.runkk.app.core.designsystem.ResultStageDefaultAvatar
import com.a207.runkk.app.core.designsystem.RunResultDetail
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPageDots
import com.a207.runkk.app.core.designsystem.RunkkPersonRow
import com.a207.runkk.app.core.designsystem.RunkkRowDivider
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.resultSheetTop
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.ui.share.RecordResultDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CrewRunResultRoute(postId: Long, viewModel: CrewRunResultViewModel = hiltViewModel()) {
    LaunchedEffect(postId) { viewModel.load(postId) }
    // 다시 읽기는 화면이 보일 때만 한다: 위에 다른 화면을 쌓거나 백그라운드면 멈추고 돌아오면 잇는다(확인 리뷰 CR2-A1)
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    CrewRunResultScreen(
        state = state,
        // 공유: 내 기록이 올라갔으면 그 기록 카드, 아니면 기록 목록(기존 동작).
        onCard = {
            val recordId = state.result?.myRecordId
            if (recordId !=
                null
            ) {
                navigator.navigate(Routes.RecordCard(recordId))
            } else {
                navigator.navigate(Routes.RECORDS)
            }
        },
        // 확인: 크루 일정으로 돌아간다(대기실은 결과를 열 때 백 스택에서 뺐다).
        onHome = navigator::back,
        onRetry = { viewModel.load(postId, isForced = true) }
    )
}

/**
 * 크루런 결과(F-16)와 결과 상세(F-16b)를 옆으로 넘긴다(대결 결과 1·2쪽과 같은 방식). 결과 쪽 위에는 무대 배경 위에 선 내 전신 아바타
 * ([ResultStage], 발끝 266, 높이 180, [임시:FR-성-33])가 있고 상세 쪽으로 넘기는 만큼 흐려진다. 머리 줄(제목, 뒤로)은 무대 위에 겹치고
 * 뒤로 버튼은 결과 쪽에만 있다(F-16b 상세 쪽은 제목만, [임시:D-성-15]). 아래에 공유(흰 알약)·확인(라임 알약).
 */
@Composable
fun CrewRunResultScreen(
    state: CrewRunResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { RESULT_PAGES }
) {
    val result = state.result
    // 배경은 홈과 같다: 아바타 꾸미기가 열렸으면 내 배경 키, 아니면 기본 무대.
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (result != null) {
            ResultStage(
                backgroundKey = if (isAvatarOpen) state.myAvatar?.backgroundKey else null,
                modifier = Modifier.graphicsLayer {
                    alpha = summaryPageVisibility(pagerState.currentPage, pagerState.currentPageOffsetFraction)
                }
            ) { slot ->
                AvatarStillSlot(AvatarStillSubject.MINE, slot) { ResultStageDefaultAvatar(Modifier.fillMaxSize()) }
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            val isSummary = result == null || pagerState.currentPage == 0
            // 머리 줄은 무대 위에 겹친다: 내용이 머리 줄 뒤(화면 맨 위)부터 시작하고, 결과 쪽 시트는 300에서 올라온다.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    result == null && state.isLoading -> RunkkLoading(Modifier.padding(top = topBarHeight))

                    result == null -> RunkkErrorState(
                        state.error?.asString() ?: stringResource(R.string.crew_run_result_load_failed),
                        onRetry = onRetry,
                        modifier = Modifier.padding(top = topBarHeight)
                    )

                    else -> ResultPager(state, result, pagerState, topBarHeight)
                }
                RunkkTopBar(
                    title = stringResource(R.string.crew_run_result_title),
                    modifier = Modifier.onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
                    onBack = if (isSummary) onHome else null
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RunkkButton(
                    stringResource(R.string.crew_run_result_card),
                    onClick = onCard,
                    style = RunkkButtonStyle.White,
                    modifier = Modifier.weight(1f)
                )
                RunkkButton(
                    stringResource(R.string.crew_run_result_home),
                    onClick = onHome,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResultPager(state: CrewRunResultUiState, result: CrewRunResult, pagerState: PagerState, topBarHeight: Dp) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                SummaryPage(result, state.myAvatar, state.mateFaces)
            } else {
                DetailPage(state.detail, state.myAvatar, topBarHeight)
            }
        }
        // 쪽 넘김 글자("밀어서 상세 결과 보기 ›"·"‹ 크루런 결과로") 바로 아래에 쪽 점(피그마 F-16·F-16b).
        val isSummary = pagerState.currentPage == 0
        PageLink(
            stringResource(if (isSummary) R.string.crew_run_result_swipe_detail else R.string.crew_run_result_back),
            onClick = { scope.launch { pagerState.animateScrollToPage(if (isSummary) 1 else 0) } }
        )
        PageDots(pagerState.currentPage)
    }
}

/** 쪽 넘김 글자 단추(11, 가운데). */
@Composable
private fun PageLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = RunkkTextStyles.Micro,
        color = RunkkColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * 결과 쪽(F-16, [임시:D-성-286], [임시:D-성-307]): 위쪽 무대(내 전신 아바타)는 화면이 깔고, 300부터 올라오는 윗모서리 둥근 시트에
 * 모집글 제목(13), 크루 합산 거리(44 Bold), 그 아래 24에 참가자 목록(얼굴·이름·거리)을 담는다. 크루원 얼굴 줄은 없다(참가자 목록에
 * 얼굴이 있다). 첫 크루런·크루 주간 반영 거리는 보이지 않는다([임시:D-성-14]).
 */
@Composable
private fun SummaryPage(result: CrewRunResult, myAvatar: MyAvatar?, mateFaces: Map<String, List<FaceLayer>>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetTop = resultSheetTop()
        val minHeight = (maxHeight - sheetTop).coerceAtLeast(0.dp)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(sheetTop))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER))
                    .background(RunkkColors.Surface)
                    .testTag(CREW_RESULT_SHEET_TAG)
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (result.title.isNotBlank()) {
                        Text(
                            result.title,
                            style = RunkkTextStyles.text(13, lineHeight = 16),
                            color = RunkkColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                    val total = RunkkFormat.km(result.totalDistance)
                    val spoken = stringResource(R.string.crew_run_result_total_spoken, total)
                    Text(
                        stringResource(R.string.crew_contribution_km, total),
                        style = RunkkTextStyles.display(44, lineHeightRatio = 1f),
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
                    )
                }
                ParticipantCard(result.participants, myAvatar, mateFaces)
            }
        }
    }
}

/**
 * 참가자 목록 카드(F-16): 표면, 테두리 1 divider, 모서리 26. 줄마다 얼굴 원 36·이름(15 Bold, 나)·거리(17 Bold), 줄 사이 점선. 대기실 입장
 * 순이다(순위가 없다).
 */
@Composable
private fun ParticipantCard(
    participants: List<CrewRunResult.Participant>,
    myAvatar: MyAvatar?,
    mateFaces: Map<String, List<FaceLayer>>
) {
    RunkkCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(horizontal = 19.dp, vertical = 5.dp),
        borderColor = RunkkColors.Border,
        verticalArrangement = Arrangement.Top
    ) {
        participants.forEachIndexed { index, p ->
            val name = if (p.isMe) stringResource(R.string.crew_member_me) else p.displayName
            val km = RunkkFormat.km(p.distance)
            val description = listOf(
                if (p.isMe) stringResource(R.string.crew_run_me_named, p.displayName) else p.displayName,
                stringResource(R.string.crew_run_km_spoken, km)
            ).joinToString(", ")
            RunkkPersonRow(
                name = name,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                avatar = { ParticipantFace(p, myAvatar, mateFaces) },
                trailing = {
                    Text(
                        stringResource(R.string.crew_contribution_km, km),
                        style = RunkkTextStyles.text(17, FontWeight.Bold, lineHeight = 20),
                        color = RunkkColors.TextPrimary
                    )
                },
                divider = if (index < participants.lastIndex) RunkkRowDivider.Dashed else RunkkRowDivider.None,
                minHeight = PARTICIPANT_ROW,
                nameStyle = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18)
            )
        }
    }
}

/** 참가자 줄 높이(얼굴 36 + 위아래 14). */
private val PARTICIPANT_ROW = 64.dp

/** 참가자 줄 얼굴 원 36(surfaceHigh 바탕): 나는 내 아바타 얼굴, 크루원은 얼굴 키·셀카 부위 그림·표시 성별의 얼굴. */
@Composable
private fun ParticipantFace(
    p: CrewRunResult.Participant,
    myAvatar: MyAvatar?,
    mateFaces: Map<String, List<FaceLayer>>
) {
    val faceKey = if (p.isMe) myAvatar?.faceKey else p.faceKey
    val layers = if (p.isMe) myAvatar?.faceLayers.orEmpty() else p.faceKey?.let(mateFaces::get).orEmpty()
    RunkkAvatarCircle(
        faceKey = faceKey,
        size = 36.dp,
        faceLayers = layers,
        character = if (p.isMe) LocalAvatarCharacter.current else AvatarCharacter.of(p.shownGender)
    )
}

/** 상세 쪽(F-16b): 러닝 결과 상세(D-282)와 같은 항목(내 기록, 아바타는 내 아바타). 기록이 아직 올라가지 않았으면 기다림 문구. */
@Composable
private fun DetailPage(detail: RecordResultDetail?, myAvatar: MyAvatar?, topBarHeight: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = topBarHeight + 11.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (detail != null) {
            val dateItems = dateItems(detail.startedAt)
            RunResultDetail(
                detail.state.copy(
                    dateLine = dateItems.joinToString(", ") { it.text },
                    dateItems = dateItems,
                    faceKey = myAvatar?.faceKey,
                    faceLayers = myAvatar?.faceLayers.orEmpty()
                )
            )
        } else {
            Text(
                stringResource(R.string.crew_run_result_detail_waiting),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}

/** 결과 상세 날짜 줄(메타 줄): [달력]11.07 금 [시계]오후 9:00 [달리기]크루런. 가운뎃점은 쓰지 않는다([임시:D-성-10]). */
@Composable
private fun dateItems(startedAt: Instant): List<MetaItem> {
    val at = startedAt.atZone(ZoneId.systemDefault())
    val date = DateTimeFormatter.ofPattern(
        stringResource(R.string.crew_run_result_date_pattern),
        Locale.KOREAN
    ).format(at)
    val time = DateTimeFormatter.ofPattern(
        stringResource(R.string.crew_run_result_time_pattern),
        Locale.KOREAN
    ).format(at)
    return listOf(
        MetaItem(RunkkIcons.CalendarToday, date),
        MetaItem(RunkkIcons.Schedule, time),
        MetaItem(RunkkIcons.DirectionsRun, stringResource(R.string.crew_run_result_kind))
    )
}

/** 쪽 점 두 개(디자인 시스템 RunkkPageDots, 간격 6, 꺼진 점 surfaceHigh). */
@Composable
private fun PageDots(current: Int) {
    RunkkPageDots(
        current = current,
        count = RESULT_PAGES,
        description = stringResource(R.string.crew_run_result_page_desc, current + 1, RESULT_PAGES),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        gap = 6.dp,
        inactiveColor = RunkkColors.SurfaceHigh
    )
}

/** 결과 쪽이 보이는 정도(0~1). 상세 쪽으로 넘어가면 0이다. */
internal fun summaryPageVisibility(currentPage: Int, offsetFraction: Float): Float =
    (1f - (currentPage + offsetFraction)).coerceIn(0f, 1f)

/** 결과 쪽 시트의 시험 표지. */
internal const val CREW_RESULT_SHEET_TAG = "crew-run-result-sheet"

private const val RESULT_PAGES = 2
private val SHEET_CORNER = 28.dp
