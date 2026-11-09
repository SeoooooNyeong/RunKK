package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunResultDetail
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPageDots
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
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
 * 흐려진다. 아래에 공유·확인.
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
            RunkkTopBar(title = stringResource(R.string.crew_run_result_title), onBack = onHome)
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
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RunkkSecondaryButton(
                    stringResource(R.string.crew_run_result_card),
                    onClick = onCard,
                    modifier = Modifier.weight(1f)
                )
                RunkkPrimaryButton(
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

/** 쪽 넘김 글자 단추(작게, 가운데). */
@Composable
private fun PageLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** 결과 쪽(F-16): 모집글 제목, 아바타 줄(가운데 내 아바타), 크루 합산 거리, 참가자 목록(이름·거리). */
@Composable
private fun SummaryPage(result: CrewRunResult, myAvatar: MyAvatar?, mateFaces: Map<String, List<FaceLayer>>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (result.title.isNotBlank()) {
            Text(
                result.title,
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        AvatarRow(result, myAvatar, mateFaces)
        val total = RunkkFormat.km(result.totalDistance)
        val spoken = stringResource(R.string.crew_run_result_total_spoken, total)
        Text(
            stringResource(R.string.crew_contribution_km, total),
            style = RunkkTextStyles.display(44),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
        )
        ParticipantCard(result.participants)
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

/** 참가자 목록 카드: 줄마다 원·이름(나)·거리. 대기실 입장 순이다(순위가 없다). */
@Composable
private fun ParticipantCard(participants: List<CrewRunResult.Participant>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunkkColors.Surface)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        participants.forEachIndexed { index, p ->
            if (index > 0) HorizontalDivider(color = RunkkColors.Border)
            val name = if (p.isMe) stringResource(R.string.crew_member_me) else p.displayName
            val km = RunkkFormat.km(p.distance)
            val description = listOf(
                if (p.isMe) stringResource(R.string.crew_run_me_named, p.displayName) else p.displayName,
                stringResource(R.string.crew_run_km_spoken, km)
            ).joinToString(", ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CrewLogo(p.displayName, size = 36, isAccent = p.isMe, modifier = Modifier.clip(CircleShape))
                Text(
                    name,
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.crew_contribution_km, km),
                    style = RunkkTextStyles.text(16, FontWeight.Bold),
                    color = RunkkColors.TextPrimary
                )
            }
        }
    }
}

/** 상세 쪽(F-16b): 러닝 결과 상세(D-282)와 같은 항목(내 기록, 아바타는 내 아바타). 기록이 아직 올라가지 않았으면 기다림 문구. */
@Composable
private fun DetailPage(detail: RecordResultDetail?, myAvatar: MyAvatar?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (detail != null) {
            RunResultDetail(
                detail.state.copy(
                    dateLine = dateLine(detail.startedAt),
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

/** 결과 상세 날짜 줄 "11.07 금 · 오후 9:00 · 크루런"(대결 결과 2쪽과 같은 형식). */
@Composable
private fun dateLine(startedAt: Instant): String {
    val pattern = stringResource(R.string.crew_run_result_date_pattern)
    val date = DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(startedAt.atZone(ZoneId.systemDefault()))
    return "$date · ${stringResource(R.string.crew_run_result_kind)}"
}

/** 쪽 점 두 개(디자인 시스템 RunkkPageDots, 결과 화면은 간격 6과 강한 테두리색). */
@Composable
private fun PageDots(current: Int) {
    RunkkPageDots(
        current = current,
        count = RESULT_PAGES,
        description = stringResource(R.string.crew_run_result_page_desc, current + 1, RESULT_PAGES),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        gap = 6.dp,
        inactiveColor = RunkkColors.BorderStrong
    )
}

/** 결과 쪽이 보이는 정도(0~1). 상세 쪽으로 넘어가면 0이다. */
private fun summaryPageVisibility(pagerState: PagerState): Float =
    (1f - (pagerState.currentPage + pagerState.currentPageOffsetFraction)).coerceIn(0f, 1f)

private const val RESULT_PAGES = 2
private const val OTHER_AVATARS = 4
