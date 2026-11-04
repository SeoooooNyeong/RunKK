package com.a207.runkk.app.ui.battle

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RunkkActionDialog
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.live.MatchFound
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 매칭 대기·초대 대기·참가 확인·성사(C-2·C-3·C-10·C-12). 측정이 시작되면 대결 진행으로 바뀐다. 초대 공유(ShareInvite)는 Activity가
 * 필요해 이 화면이 [sharer]로 보낸다. 참가하기는 OS 위치 권한이 없으면 권한 창을 먼저 띄운다(D-267, 거절은 진행 화면이 안내한다).
 */
@Composable
fun MatchRoute(start: MatchStart, sharer: InviteSharer, viewModel: MatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel, start) {
        when (start) {
            is MatchStart.Quick -> viewModel.startQuick(start.distance)
            is MatchStart.Invite -> viewModel.startInvite(start.distance)
            is MatchStart.Join -> viewModel.startJoin(start.token)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MatchEvent.Started -> navigator.replace(event.key)

                MatchEvent.Exit -> navigator.back()

                // 공유 화면이 열리고 닫히기를 기다리는 동안 다음 이벤트(성사·안내)를 막지 않게 따로 띄운다.
                is MatchEvent.ShareInvite -> scope.launch {
                    val activity = context as? Activity
                    val shared =
                        activity?.let { sharer.share(it, event.invite, event.inviterName) } ?: ShareResult.FAILED
                    if (shared == ShareResult.FAILED) viewModel.onShareFailed()
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onAccept()
    }
    val accept = {
        val granted = LOCATION_PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (granted) viewModel.onAccept() else permission.launch(LOCATION_PERMISSIONS)
    }
    // 대기 중 뒤로가기는 취소, 성사 뒤에는 자동 출발이라 막는다(FR-087).
    BackHandler { viewModel.onCancel() }
    MatchScreen(
        state = state,
        actions = MatchActions(
            onCancel = viewModel::onCancel,
            onForfeit = viewModel::onForfeit,
            onResend = viewModel::onResend,
            onAccept = accept,
            onDecline = viewModel::onDecline,
            onInviteEndedConfirmed = viewModel::onInviteEndedConfirmed,
            onNoticeConfirmed = viewModel::onNoticeConfirmed,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed
        )
    )
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun MatchScreen(state: MatchUiState, actions: MatchActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)
    ) {
        when (state.phase) {
            MatchPhase.REQUESTING, MatchPhase.WAITING, MatchPhase.CANCELLING, MatchPhase.INVITING ->
                Waiting(state, actions)

            MatchPhase.INVITE_ENDED -> InviteEnded(state, actions.onInviteEndedConfirmed)

            MatchPhase.CONFIRMING, MatchPhase.JOINING -> Confirming(state, actions)

            MatchPhase.MATCHED -> state.match?.let { Matched(it, state, actions.onForfeit) }
        }
    }
    state.notice?.let { notice ->
        RunkkActionDialog(
            title = notice.asString(),
            confirmLabel = stringResource(R.string.battle_notice_confirm),
            onConfirm = actions.onNoticeConfirmed,
            onDismissRequest = actions.onNoticeConfirmed
        )
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(
            onAgree = actions.onConsentAgree,
            onClose = actions.onConsentClose
        )
    }
}

/** 랜덤 대기(C-2)와 초대 대기(C-10): 가운데 찾는 자세 아바타(D84), 안내, 경과, 아래 버튼. */
@Composable
private fun ColumnScope.Waiting(state: MatchUiState, actions: MatchActions) {
    val isInvite = state.opponentKind == OpponentKind.FRIEND
    val title = stringResource(if (isInvite) R.string.battle_invite_waiting else R.string.battle_match_finding)
    val description = stringResource(R.string.battle_waiting_desc, title, waitLabel(state.waitingSeconds))
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        RunkkAvatar(null, AvatarExpression.SEARCH, Modifier.size(width = 150.dp, height = 240.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
        ) {
            Text(title, style = RunkkTextStyles.Headline, color = RunkkColors.TextPrimary, textAlign = TextAlign.Center)
            Text(waitLabel(state.waitingSeconds), style = RunkkTextStyles.display(32), color = RunkkColors.TextPrimary)
        }
        state.error?.let {
            Text(
                it.asString(),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    val isOpen = state.phase == MatchPhase.WAITING || state.phase == MatchPhase.INVITING
    if (isInvite) {
        RunkkPrimaryButton(
            stringResource(R.string.battle_invite_resend),
            onClick = actions.onResend,
            isEnabled = state.phase == MatchPhase.INVITING,
            modifier = Modifier.fillMaxWidth()
        )
        RunkkSecondaryButton(
            stringResource(R.string.battle_invite_cancel),
            onClick = actions.onCancel,
            isEnabled = isOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    } else {
        RunkkPrimaryButton(
            stringResource(R.string.battle_match_cancel),
            onClick = actions.onCancel,
            isEnabled = isOpen,
            isLoading = state.phase == MatchPhase.CANCELLING,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 초대가 끝남(D-271): 만료 "다시 초대하기", 거절 "확인". 누르면 배틀런 화면 친구 분절로 돌아간다. */
@Composable
private fun ColumnScope.InviteEnded(state: MatchUiState, onConfirm: () -> Unit) {
    val isDeclined = state.inviteEnd == MatchUiState.InviteEnd.DECLINED
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        RunkkAvatar(null, AvatarExpression.SAD, Modifier.size(200.dp))
        Text(
            stringResource(if (isDeclined) R.string.battle_invite_declined else R.string.battle_invite_expired),
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    RunkkPrimaryButton(
        stringResource(if (isDeclined) R.string.battle_notice_confirm else R.string.battle_invite_reinvite),
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 참가 확인(C-12): "친구가 대결을 신청했어요", 나 VS 초대한 친구(티어, 최고 기록), 거절·참가하기. */
@Composable
private fun ColumnScope.Confirming(state: MatchUiState, actions: MatchActions) {
    val invite = state.invite
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.battle_join_headline),
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        if (invite != null) InviteVersus(invite)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RunkkSecondaryButton(
            stringResource(R.string.battle_join_decline),
            onClick = actions.onDecline,
            isEnabled = invite != null && state.phase == MatchPhase.CONFIRMING,
            modifier = Modifier.weight(1f)
        )
        RunkkPrimaryButton(
            stringResource(R.string.battle_join_accept),
            onClick = actions.onAccept,
            isEnabled = invite != null,
            isLoading = state.phase == MatchPhase.JOINING,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InviteVersus(invite: FriendInviteView) {
    val distance = distanceLabel(invite.distance).asString()
    Versus(
        mine = {
            Contender(
                stringResource(R.string.battle_me),
                it,
                tier = tierLabel(invite.myTier),
                caption = bestLine(distance, invite.myBestSeconds?.let(Duration::ofSeconds))
            )
        },
        theirs = {
            Contender(
                invite.inviter.name,
                it,
                figure = Figure.RIVAL,
                tier = tierLabel(invite.inviter.tier),
                caption = bestLine(distance, invite.inviter.bestSeconds?.let(Duration::ofSeconds))
            )
        }
    )
}

/**
 * 성사(C-3): "상대를 찾았어요", 나(티어, R2-10) VS 상대(티어, 최고 기록), 자동 출발 고리. 측정이 늦어지면 그만두기(배틀 QA 2번).
 * 배치고사 상대는 점선 원 「?」로 그리고 티어를 보이지 않는다(FR-276, J24-3: UNRANKED를 「배치 중」으로 그리지 않는다).
 */
@Composable
private fun ColumnScope.Matched(match: MatchFound, state: MatchUiState, onForfeit: () -> Unit) {
    val distance = distanceLabel(match.distance).asString()
    val isHidden = state.isOpponentHidden
    val opponentTier = if (isHidden) null else tierLabel(match.opponentTier)
    val description = stringResource(
        R.string.battle_match_found_desc,
        match.opponentName,
        opponentTier ?: stringResource(R.string.battle_match_placement_opponent)
    )
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            stringResource(R.string.battle_match_found),
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
        )
        Versus(
            mine = {
                Contender(
                    stringResource(R.string.battle_me),
                    it,
                    tier = state.myTier?.let { tier -> tierLabel(tier) },
                    caption = bestLine(distance, match.myBest)
                )
            },
            theirs = {
                Contender(
                    match.opponentName,
                    it,
                    figure = if (isHidden) Figure.UNKNOWN else Figure.RIVAL,
                    tier = opponentTier,
                    caption = bestLine(distance, match.opponentBest),
                    character = AvatarCharacter.of(match.opponentGender)
                )
            }
        )
        StartRing(state.startsInSeconds, total = MATCH_START_SECONDS)
    }
    // 측정이 늦어지면(배틀 QA 2번) 뒤로가기가 막힌 이 화면에서 나갈 길을 준다.
    if (state.canForfeit) {
        RunkkSecondaryButton(
            stringResource(R.string.battle_match_forfeit),
            onClick = onForfeit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 티어 표기. 배치 전(UNRANKED)은 랜덤 대결의 배치 전 표기를 그대로 쓴다(D-274). */
@Composable
private fun tierLabel(tier: String): String = tierName(tier) ?: tier

internal fun waitLabel(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60

/** 성사 뒤 자동 출발까지(성사 화면 10초, 서버 startAt = 성사 + 3초 + 10초). 고리 몫의 분모다. */
private const val MATCH_START_SECONDS = 10L

@Preview(name = "매칭 대기", widthDp = 390, heightDp = 844)
@Composable
private fun MatchWaitingPreview() {
    RunkkTheme(reduceMotion = true) {
        MatchScreen(MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7), MatchActions())
    }
}

@Preview(name = "참가 확인", widthDp = 390, heightDp = 844)
@Composable
private fun MatchConfirmPreview() {
    RunkkTheme(reduceMotion = true) {
        MatchScreen(
            MatchUiState(
                phase = MatchPhase.CONFIRMING,
                opponentKind = OpponentKind.FRIEND,
                invite = FriendInviteView(
                    1,
                    "PENDING",
                    Distance(5_000),
                    Instant.EPOCH,
                    FriendInviteView.Inviter("바람처럼", "GOLD", 1_788),
                    "SILVER",
                    1_812,
                    null
                )
            ),
            MatchActions()
        )
    }
}
