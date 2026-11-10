package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.RunResultDetail
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPageDots
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.FaceLayer
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
 * 크루런 결과(F-16)와 결과 상세(F-16b)를 옆으로 넘긴다(대결 결과 1·2쪽과 같은 방식). 배경 그림은 결과 쪽에만 있고 상세 쪽으로 넘기는 만큼
 * 흐려진다. 머리 줄 뒤로 버튼은 결과 쪽에만 있다(F-16b 상세 쪽은 제목만, [임시:D-성-15]). 아래에 공유(흰 알약)·확인(라임 알약).
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
    // 배경은 홈과 같다: 아바타 꾸미기가 열렸으면 내 배경 키, 아니면 기본 배경.
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (result != null) {
            AvatarBackdrop(
                backgroundKey = if (isAvatarOpen) state.myAvatar?.backgroundKey else null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = summaryPageVisibility(pagerState) }
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            val isSummary = result == null || pagerState.currentPage == 0
            RunkkTopBar(
                title = stringResource(R.string.crew_run_result_title),
                onBack = if (isSummary) onHome else null
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    result == null && state.isLoading -> RunkkLoading()

                    result == null -> RunkkErrorState(
                        state.error?.asString() ?: stringResource(R.string.crew_run_result_load_failed),
                        onRetry = onRetry
                    )

                    else -> ResultPager(state, result, pagerState)
                }
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
private fun ResultPager(state: CrewRunResultUiState, result: CrewRunResult, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page ==
                0
            ) {
                SummaryPage(result, state.myAvatar, state.mateFaces)
            } else {
                DetailPage(state.detail, state.myAvatar)
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
 * 결과 쪽(F-16): 모집글 제목(13), 아바타 줄(가운데 내 아바타), 크루 합산 거리(44 Bold) 묶음(사이 16), 그 아래 24에 참가자 목록(이름·거리).
 * 첫 크루런·크루 주간 반영 거리는 보이지 않는다([임시:D-성-14]).
 */
@Composable
private fun SummaryPage(result: CrewRunResult, myAvatar: MyAvatar?, mateFaces: Map<String, List<FaceLayer>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp),
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
            AvatarRow(result, myAvatar, mateFaces)
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

/**
 * 아바타 줄: 가운데 크게 내 아바타(홈과 같은 얼굴, 체형은 앱의 LocalAvatarCharacter), 양옆에 크루원 넷까지(그 크루원의 얼굴 키·셀카 부위
 * 그림·표시 성별, 계약 crewrun-sum v1.2).
 */
@Composable
private fun AvatarRow(result: CrewRunResult, myAvatar: MyAvatar?, mateFaces: Map<String, List<FaceLayer>>) {
    val others = result.participants.filter { !it.isMe }.take(OTHER_AVATARS)
    val left = others.take((others.size + 1) / 2)
    val right = others.drop(left.size)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        left.forEach { MateAvatar(it, mateFaces) }
        RunkkAvatar(
            faceKey = myAvatar?.faceKey,
            expression = AvatarExpression.JOY,
            modifier = Modifier.size(width = 104.dp, height = 140.dp),
            faceLayers = myAvatar?.faceLayers.orEmpty()
        )
        right.forEach { MateAvatar(it, mateFaces) }
    }
}

/**
 * 크루원 아바타 한 칸: 기본 캐릭터 얼굴이면 그 그림, 셀카 얼굴이면 받은 부위 그림이다. 얼굴 키가 없거나(얼굴을 고르지 않음·옛 서버)
 * 셀카 부위 그림을 아직 받지 못했으면 지금처럼 기본 캐릭터다(빈 칸을 그리지 않는다).
 */
@Composable
private fun MateAvatar(mate: CrewRunResult.Participant, mateFaces: Map<String, List<FaceLayer>>) {
    val layers = mate.faceKey?.let(mateFaces::get).orEmpty()
    RunkkAvatar(
        faceKey = mate.faceKey?.takeIf { RunkkAvatarArt.characterOf(it) != null || layers.isNotEmpty() },
        expression = AvatarExpression.JOY,
        modifier = Modifier.size(width = 50.dp, height = 62.dp),
        faceLayers = layers,
        character = AvatarCharacter.of(mate.shownGender)
    )
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
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 19.dp, vertical = 5.dp)
    ) {
        participants.forEachIndexed { index, p ->
            if (index > 0) DashedDivider()
            val name = if (p.isMe) stringResource(R.string.crew_member_me) else p.displayName
            val km = RunkkFormat.km(p.distance)
            val description = listOf(
                if (p.isMe) stringResource(R.string.crew_run_me_named, p.displayName) else p.displayName,
                stringResource(R.string.crew_run_km_spoken, km)
            ).joinToString(", ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 49.dp)
                    .padding(vertical = 14.dp)
                    .clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParticipantFace(p, myAvatar, mateFaces)
                Text(
                    name,
                    style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.crew_contribution_km, km),
                    style = RunkkTextStyles.text(17, FontWeight.Bold, lineHeight = 20),
                    color = RunkkColors.TextPrimary
                )
            }
        }
    }
}

/** 참가자 줄 얼굴 원 36(surfaceHigh 바탕): 나는 내 아바타 얼굴, 크루원은 얼굴 키·셀카 부위 그림·표시 성별의 얼굴. */
@Composable
private fun ParticipantFace(
    p: CrewRunResult.Participant,
    myAvatar: MyAvatar?,
    mateFaces: Map<String, List<FaceLayer>>
) {
    val faceKey = if (p.isMe) myAvatar?.faceKey else p.faceKey
    val layers = if (p.isMe) myAvatar?.faceLayers.orEmpty() else p.faceKey?.let(mateFaces::get).orEmpty()
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(RunkkColors.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        RunkkAvatar(
            faceKey = faceKey,
            expression = AvatarExpression.FACE,
            modifier = Modifier.fillMaxSize(),
            faceLayers = layers,
            character = if (p.isMe) LocalAvatarCharacter.current else AvatarCharacter.of(p.shownGender)
        )
    }
}

/** 참가자 줄 사이 점선(1, divider). */
@Composable
private fun DashedDivider() {
    val color = RunkkColors.Border
    Box(
        Modifier.fillMaxWidth().height(1.dp).drawBehind {
            val dash = 4.dp.toPx()
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
            )
        }
    )
}

/** 상세 쪽(F-16b): 러닝 결과 상세(D-282)와 같은 항목(내 기록, 아바타는 내 아바타). 기록이 아직 올라가지 않았으면 기다림 문구. */
@Composable
private fun DetailPage(detail: RecordResultDetail?, myAvatar: MyAvatar?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 12.dp),
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
private fun summaryPageVisibility(pagerState: PagerState): Float =
    (1f - (pagerState.currentPage + pagerState.currentPageOffsetFraction)).coerceIn(0f, 1f)

private const val RESULT_PAGES = 2
private const val OTHER_AVATARS = 4
