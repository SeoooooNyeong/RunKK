package com.a207.runkk.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor

/**
 * 반응한 사람 시트(피그마 F-09d, [임시:D-성-24]): W1 RunkkBottomSheet(제목 「반응」, 닫기 ✕). 탭 줄은 「전체 n」과 수가 있는 종류(아이콘 +
 * 수)이고 고른 탭은 보라 채움, 나머지는 테두리. 목록 줄은 높이 52, 아래 구분선, 아바타 32 + 닉네임 15와 오른쪽 반응 아이콘 18. 반응 한 건이
 * 한 줄이라 두 종류를 단 사람은 두 줄이다(contract-W5 W5-D6).
 */
@Composable
internal fun ReactorsSheet(state: ChatUiState, actions: ChatActions) {
    val target = state.reactorsTarget ?: return
    RunkkBottomSheet(onDismissRequest = actions.onCloseReactors, title = stringResource(R.string.chat_reactors_title)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val total = state.reactorCounts.values.sum()
            ReactorTab(
                label = stringResource(R.string.chat_reactors_all, total),
                spoken = stringResource(R.string.chat_reactors_all, total),
                kind = null,
                isSelected = state.reactorsTab == null,
                onSelect = { actions.onReactorTab(null) }
            )
            state.reactorTabs.forEach { kind ->
                val count = state.reactorCounts[kind] ?: 0
                ReactorTab(
                    label = count.toString(),
                    spoken = stringResource(R.string.chat_reaction_description, stringResource(kind.labelRes()), count),
                    kind = kind,
                    isSelected = state.reactorsTab == kind,
                    onSelect = { actions.onReactorTab(kind) }
                )
            }
        }
        when {
            state.isLoadingReactors -> Box(Modifier.fillMaxWidth().height(104.dp)) { RunkkLoading() }

            state.isReactorsFailed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.chat_reactors_failed),
                    style = RunkkTextStyles.text(14, lineHeight = 21),
                    color = RunkkColors.TextPrimary
                )
                RunkkTextLink(
                    stringResource(R.string.chat_reactors_retry),
                    onClick = { actions.onOpenReactors(target, state.reactorsTab) }
                )
            }

            else -> Column { state.visibleReactors.forEach { ReactorRow(it) } }
        }
    }
}

/** 종류 탭(높이 32, r16, 좌우 12). 고른 탭은 보라 채움 흰 글자, 나머지는 테두리 1. 누름 칸 48. */
@Composable
private fun ReactorTab(label: String, spoken: String, kind: ReactionKind?, isSelected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val content = if (isSelected) RunkkColors.OnSecondary else RunkkColors.TextPrimary
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onSelect)
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .height(32.dp)
                .clip(shape)
                .then(
                    if (isSelected) {
                        Modifier.background(RunkkColors.Violet)
                    } else {
                        Modifier.border(1.dp, RunkkColors.Border, shape)
                    }
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (kind !=
                null
            ) {
                Icon(kind.icon(), contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(label, style = RunkkTextStyles.text(13, FontWeight.Medium, lineHeight = 16), color = content)
        }
    }
}

/** 반응한 사람 한 줄(높이 52, 아래 구분선 1): 아바타 32, 닉네임 15, 오른쪽 반응 아이콘 18. */
@Composable
private fun ReactorRow(reactor: Reactor) {
    val kindName = stringResource(reactor.kind.labelRes())
    val description = stringResource(R.string.chat_reactor_row, reactor.displayName, kindName)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(
            Modifier.fillMaxWidth().height(51.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(RunkkColors.SurfaceHigh))
            Text(
                reactor.displayName,
                style = RunkkTextStyles.text(15, lineHeight = 18),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                reactor.kind.icon(),
                contentDescription = null,
                tint = RunkkColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RunkkColors.Border))
    }
}
