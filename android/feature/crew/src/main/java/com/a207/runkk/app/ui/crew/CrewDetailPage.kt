package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.MyCrew
import java.time.Duration

/** 크루 정보 화면(와이어 I1): 뒤로, 상세 내용. 시스템 뒤로 가기도 목록으로 돌아간다. */
@Composable
internal fun CrewDetailPage(state: CrewFindUiState, crew: CrewList.Item, actions: CrewFindActions) {
    BackHandler(onBack = actions.onCloseDetail)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RunkkTopBar(title = stringResource(R.string.crew_detail_title), onBack = actions.onCloseDetail)
        CrewDetailContent(
            crew,
            pending = state.pending.firstOrNull { it.crewId == crew.crewId },
            isJoining = state.isJoining,
            onJoin = { actions.onJoin(crew) },
            onCancel = actions.onCancelAsk,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 크루 상세(크루 밖 열람 범위: 로고, 이름, 인원과 가입 방식, 소개, 태그, 이번 주 크루 거리, 크루장, Q-45). 이미 가입 신청을 보낸
 * 크루면([pending]) 결정 기한(신청 뒤 7일, FR-121)과 신청 취소를, 아니면 가입 단추를 아래에 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewDetailContent(
    crew: CrewList.Item,
    isJoining: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    pending: MyCrew.PendingJoin? = null,
    onCancel: (MyCrew.PendingJoin) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CrewLogo(crew.name, size = 72, isAccent = true)
                Text(
                    crew.name,
                    style = RunkkTextStyles.TopBarTitle,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    listOf(
                        stringResource(R.string.crew_meta_headcount, crew.headcount, CREW_MAX),
                        stringResource(crew.joinPolicy.longLabelRes)
                    ).joinToString(", "),
                    style = RunkkTextStyles.Caption,
                    color = colors.onSurfaceVariant
                )
            }
            crew.intro?.let { Text(it, style = RunkkTextStyles.Body, color = colors.onSurface) }
            if (crew.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    crew.tags.forEach { RunkkBadge(RunkkTags.label(it)) }
                }
            }
            CrewCard {
                DetailLine(stringResource(R.string.crew_detail_weekly), RunkkFormat.targetLabel(crew.weeklyDistance))
                HorizontalDivider(color = colors.outlineVariant)
                DetailLine(stringResource(R.string.crew_role_leader), crew.leaderName)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
            if (pending != null) {
                PendingNotice(pending, isBusy = isJoining, onCancel = { onCancel(pending) })
            } else {
                JoinButton(crew, isJoining, onJoin)
            }
        }
    }
}

/** 상세의 한 줄: 이름과 값. */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = RunkkTextStyles.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = RunkkTextStyles.BodyStrong, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 가입 단추: 정원이 차면 누를 수 없고, 바로 가입이면 "바로 가입하기", 승인제면 "가입 신청하기". */
@Composable
private fun JoinButton(crew: CrewList.Item, isJoining: Boolean, onJoin: () -> Unit) {
    val full = crew.headcount >= CREW_MAX
    RunkkPrimaryButton(
        text = stringResource(
            when {
                full -> R.string.crew_detail_full
                crew.joinPolicy == JoinPolicy.FREE -> R.string.crew_detail_join_free
                else -> R.string.crew_detail_join_apply
            }
        ),
        onClick = onJoin,
        isEnabled = !full,
        isLoading = isJoining,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 가입 신청 대기(와이어 I1): "가입 신청을 보냈어요", "M.d까지 결정돼요", 신청 취소. */
@Composable
private fun PendingNotice(pending: MyCrew.PendingJoin, isBusy: Boolean, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CrewCard(isHighlighted = true) {
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.crew_detail_pending_title),
                    style = RunkkTextStyles.BodyStrong,
                    color = colors.onSurface
                )
                Text(
                    stringResource(
                        R.string.crew_detail_pending_until,
                        shortDateLabel(pending.requestedAt.plus(Duration.ofDays(JOIN_REQUEST_DAYS)))
                    ),
                    style = RunkkTextStyles.Caption,
                    color = colors.onSurfaceVariant
                )
            }
        }
        RunkkSecondaryButton(
            stringResource(R.string.crew_find_cancel_confirm_action),
            onClick = onCancel,
            isEnabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 가입 신청은 신청 뒤 7일 안에 결정이 없으면 만료된다(FR-121, Q-40). */
private const val JOIN_REQUEST_DAYS = 7L

/** 상세와 만들기 화면의 가입 방식 이름(와이어 I1, I2): "바로 가입", "승인 후 가입". 목록 배지는 짧은 이름([labelRes])이다. */
internal val JoinPolicy.longLabelRes: Int
    @StringRes get() = when (this) {
        JoinPolicy.APPROVAL -> R.string.crew_join_policy_approval_long
        JoinPolicy.FREE -> R.string.crew_join_policy_free
    }
