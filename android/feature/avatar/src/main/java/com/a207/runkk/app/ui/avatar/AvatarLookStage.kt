package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.a207.runkk.app.core.avatar.AvatarStageFrame
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.RiveAvatarStage
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.avatarStageBackdropRes
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 카드 무대(피그마 제안 B안 무대 언어: 내 러너·꾸미기·상점·새 얼굴이 같이 쓴다). 고른 배경을 아래 가운데로 잘라 깔고 바닥선에 내 아바타를
 * 세운다(design-oo 05-30 4절, 계산은 core `RiveAvatarStage`·`AvatarStageFrame`). 칸 크기는 부르는 쪽이 정한다.
 *
 * - [look]이 null이면(읽는 중) 배경과 그림자 위에 로딩 표시만 둔다([임시:D-성-106]).
 * - Rive를 그리지 못하면(네이티브 없음·자산 실패) 같은 바닥선에 앞 단계 서기 그림([LegacyStandingAvatar])을 세운다. 오류 문구는 없다.
 * - 기본 배경은 다크 벌에서 같은 장면의 밤 그림이다(core AvatarBackdrop과 같다).
 */
@Composable
internal fun AvatarLookStage(
    look: AvatarLook?,
    backgroundKey: String?,
    faceKey: String?,
    faceLayers: List<FaceLayer>,
    modifier: Modifier = Modifier,
    /** 그림 하나로 읽어 줄 설명. 기본 그림(Rive 실패)이면 참을 받는다(기본 얼굴 이름은 그때만 읽는다, 실기기 DV3-10). */
    description: (@Composable (isFallback: Boolean) -> String)? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val isDark = LocalRunkkColors.current.isDark
    val backdrop = painterResource(avatarStageBackdropRes(backgroundKey, isDark = isDark))
    val gender = myAvatarGender()
    var isFailed by remember(gender, look?.headLayers) { mutableStateOf(false) }
    val spoken = description?.invoke(isFailed)
    val described = if (spoken == null) modifier else modifier.semantics { contentDescription = spoken }
    if (isFailed) {
        AvatarStageFrame(modifier = described, backdrop = backdrop, darkTheme = isDark, overlay = overlay) {
            LegacyStandingAvatar(faceKey, faceLayers)
        }
    } else {
        Box(described) {
            RiveAvatarStage(
                look = look?.let {
                    RiveAvatarLook(gender, it.outfit, it.bodyShape, it.runAmount, it.headLayers)
                },
                darkTheme = isDark,
                modifier = Modifier.fillMaxSize(),
                backdrop = backdrop,
                loadingColor = RunkkColors.TextSecondary,
                onError = { isFailed = true }
            )
            overlay()
        }
    }
}
