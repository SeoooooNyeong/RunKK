package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/** 홈 칸 진행 배지의 시험 태그. */
internal const val SELFIE_WORKING_BADGE_TAG = "selfie-working-badge"

/**
 * 홈 아바타 칸 위 「얼굴 만드는 중」 배지(피그마 AV-7 4 진행 배지, 62329:1710): 흰 92% 알약, 돌아가는 고리 16(보라) + 13 Bold. 서버가 진행률을
 * 주지 않아 %는 없다. 누르면 얼굴 만들기 화면이 만드는 중 단계로 열린다.
 */
@Composable
internal fun SelfieWorkingBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.avatar_selfie_home_badge)
    val action = stringResource(R.string.avatar_selfie_home_badge_action)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(SELFIE_WORKING_BADGE_TAG)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(RunkkColors.Surface.copy(alpha = BADGE_ALPHA))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).clearAndSetSemantics {},
                color = RunkkColors.Violet,
                trackColor = RunkkColors.SurfaceHigh,
                strokeWidth = 2.dp
            )
            Text(
                label,
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

private const val BADGE_ALPHA = 0.92f
