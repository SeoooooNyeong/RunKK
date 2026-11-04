package com.a207.runkk.app.ui.battle

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

// 랜덤 대결·배치고사 진행·친구 대결이 함께 쓰는 부품(피그마 61355:2909·61355:3061·61312:14822, design-oo 05-21 7.1).

/**
 * 이동 함수: 경로 이름 키(Routes.Key, 랜덤 대결 ↔ 배치고사 진행)는 맨 위를 바꿔 열고(D-499), 매칭·초대 대기는 OS 위치 권한을 확인한 뒤
 * 위에 연다(권한 답과 상관없이 간다. 거절은 대결 시작 때 안내한다, 배틀런 화면 D-268과 같다).
 */
@Composable
internal fun rememberBattleOpener(): (NavKey) -> Unit {
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<NavKey?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        waiting?.let(navigator::navigate)
        waiting = null
    }
    return { key ->
        when {
            key is Routes.Key -> navigator.replace(key)

            LOCATION_PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } ->
                navigator.navigate(key)

            else -> {
                waiting = key
                permission.launch(LOCATION_PERMISSIONS)
            }
        }
    }
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * 매칭 히어로(피그마 61355:2909): 보라 바탕 r24에 내 아바타 「VS」 흰 점선 원 「?」와 「비슷한 페이스의 러너를 찾아 드려요」.
 * 보조 기술은 「나 대 아직 모르는 상대…」 한 줄로 읽는다.
 */
@Composable
internal fun MatchHero(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.random_battle_versus_desc)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunkkColors.Violet)
            .padding(vertical = 20.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            RunkkAvatar(null, AvatarExpression.STAND, Modifier.size(width = 70.dp, height = 130.dp))
            Text("VS", style = RunkkTextStyles.text(24, FontWeight.Bold), color = Color.White)
            QuestionCircle(size = 100.dp, color = Color.White)
        }
        Text(
            stringResource(R.string.random_battle_hero),
            style = RunkkTextStyles.text(16, FontWeight.Bold),
            color = Color.White
        )
    }
}

/** 구역 제목(「거리 선택」 16 Bold). */
@Composable
internal fun EntrySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = RunkkTextStyles.text(16, FontWeight.Bold),
        color = RunkkColors.TextPrimary,
        modifier = modifier.semantics { heading() }
    )
}

/**
 * 거리 칩 넷(1·3·5·10km, 피그마 61355:2909): 같은 폭, 알약. 고름 = 보라 바탕 흰 15 Bold, 안 고름 = 표면 + 1 테두리 15 Medium.
 * 라디오 묶음으로 읽고 고른 칩은 「선택됨」이다. 터치 영역은 48 이상.
 */
@Composable
internal fun DistanceChips(selected: Distance, onSelect: (Distance) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatchUiState.DISTANCES.forEach { distance ->
            val isSelected = distance == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) RunkkColors.Violet else RunkkColors.Surface)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, RunkkColors.Border, CircleShape))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(distance) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    RunkkFormat.targetLabel(distance),
                    style = RunkkTextStyles.text(15, if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = if (isSelected) Color.White else RunkkColors.TextPrimary
                )
            }
        }
    }
}

/**
 * 하단 고정 바의 보라 주 버튼(피그마 61355:2909 하단: 표면, 위쪽 둥근 20, pt12 pb24 px20, 버튼 52 보라 채움 흰 14 Medium).
 * [isEnabled]가 거짓이면 흐리게 두고 누를 수 없다.
 */
@Composable
internal fun VioletBottomBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    val top = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, top, ambientColor = BAR_SHADOW, spotColor = BAR_SHADOW)
            .clip(top)
            .background(RunkkColors.Surface)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .alpha(if (isEnabled) 1f else FADED)
                .clip(CircleShape)
                .background(RunkkColors.Violet)
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = RunkkTextStyles.text(14, FontWeight.Medium), color = Color.White)
        }
    }
}

/** 카드·하단 바 그림자 색(피그마 rgba(23,26,18,0.08~0.16) 근사). */
internal val BAR_SHADOW = Color(0x29171A12)
