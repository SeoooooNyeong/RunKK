package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 셀럽 그림(셀럽런 목록·카운트다운·진행 마커·결과). 서버가 준 셀럽 그림 키([figureKey], celebrityFigure)가 있으면 그 셀럽 전용
 * 캐릭터의 서 있는 그림을 그린다(사용자 결정 2026-10-31). 특징만 살린 그림이라 흐리게 하지 않는다. 키가 없거나 모르는 키면
 * 공통 흐린 그림(D-254, 결정 D74: 피그마 셀럽런 결과 E-6의 캐릭터를 실제 인물과 닮지 않게 흐리게)이다.
 * [colorFilter]는 전용 그림에만 입힌다(공통 그림은 흐려서 장면에 묻힌다). 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun CelebrityFigure(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    figureKey: String? = null,
    colorFilter: ColorFilter? = null
) {
    val celebrity = CelebrityCharacter.of(figureKey)
    Image(
        painter = painterResource(celebrity?.stand ?: R.drawable.celebrity_figure),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // 전용 그림은 기본 아바타처럼 칸 바닥에 발을 붙인다
        alignment = if (celebrity != null) Alignment.BottomCenter else Alignment.Center,
        colorFilter = if (celebrity != null) colorFilter else null,
        modifier = modifier
            .size(size)
            .alpha(if (celebrity != null) 1f else CELEBRITY_FIGURE_ALPHA)
            .testTag(CELEBRITY_FIGURE_TAG)
            .clearAndSetSemantics {}
    )
}

/**
 * 셀럽 전용 캐릭터(서버 celebrityFigure "lee"·"hwang"·"son"·"joo"·"kim" 등). 서 있는 그림과 달리기 한 주기 4장(오른쪽을 향함)은 기본 아바타
 * ([defaultAvatarOf]·[runCycleOf])와 같은 구성이라 [RunkkRunningFigure]에 그대로 넘긴다. [face]는 얼굴 그림이 있는 셀럽(코치)만 갖는다
 * (셀럽런 목록의 둥근 얼굴, 피그마 E-1).
 */
enum class CelebrityCharacter(
    @DrawableRes val stand: Int,
    val runCycle: List<Int>,
    @DrawableRes val face: Int? = null
) {
    LEE(
        R.drawable.celebrity_lee_stand,
        listOf(
            R.drawable.celebrity_lee_run_1,
            R.drawable.celebrity_lee_run_2,
            R.drawable.celebrity_lee_run_3,
            R.drawable.celebrity_lee_run_4
        )
    ),
    HWANG(
        R.drawable.celebrity_hwang_stand,
        listOf(
            R.drawable.celebrity_hwang_run_1,
            R.drawable.celebrity_hwang_run_2,
            R.drawable.celebrity_hwang_run_3,
            R.drawable.celebrity_hwang_run_4
        )
    ),
    SON(
        R.drawable.celebrity_son_stand,
        listOf(
            R.drawable.celebrity_son_run_1,
            R.drawable.celebrity_son_run_2,
            R.drawable.celebrity_son_run_3,
            R.drawable.celebrity_son_run_4
        )
    ),
    GIAN(
        R.drawable.celebrity_gian_stand,
        listOf(
            R.drawable.celebrity_gian_run_1,
            R.drawable.celebrity_gian_run_2,
            R.drawable.celebrity_gian_run_3,
            R.drawable.celebrity_gian_run_4
        )
    ),
    JIN(
        R.drawable.celebrity_jin_stand,
        listOf(
            R.drawable.celebrity_jin_run_1,
            R.drawable.celebrity_jin_run_2,
            R.drawable.celebrity_jin_run_3,
            R.drawable.celebrity_jin_run_4
        )
    ),
    LIM(
        R.drawable.celebrity_lim_stand,
        listOf(
            R.drawable.celebrity_lim_run_1,
            R.drawable.celebrity_lim_run_2,
            R.drawable.celebrity_lim_run_3,
            R.drawable.celebrity_lim_run_4
        )
    ),

    /** SSAFY 코치 실명 셀럽(2026-11-08, D130). 서버 키 "joo"·"kim" */
    JOO(
        R.drawable.celebrity_joo_stand,
        listOf(
            R.drawable.celebrity_joo_run_1,
            R.drawable.celebrity_joo_run_2,
            R.drawable.celebrity_joo_run_3,
            R.drawable.celebrity_joo_run_4
        ),
        R.drawable.celebrity_joo_face
    ),
    KIM(
        R.drawable.celebrity_kim_stand,
        listOf(
            R.drawable.celebrity_kim_run_1,
            R.drawable.celebrity_kim_run_2,
            R.drawable.celebrity_kim_run_3,
            R.drawable.celebrity_kim_run_4
        ),
        R.drawable.celebrity_kim_face
    );

    companion object {
        /** 서버 키 → 전용 캐릭터. 대소문자는 가리지 않고, 없거나 모르는 키면 null(공통 흐린 그림)이다. */
        fun of(key: String?): CelebrityCharacter? =
            key?.trim()?.let { k -> entries.firstOrNull { it.name.equals(k, ignoreCase = true) } }
    }
}

/**
 * 셀럽런 목록의 셀럽 그림(피그마 E-1): 얼굴 그림이 있는 셀럽(코치)은 지름 [size]의 둥근 얼굴, 없으면 [CelebrityFigure]의 서 있는 그림이다.
 * 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun CelebrityPortrait(modifier: Modifier = Modifier, size: Dp = 58.dp, figureKey: String? = null) {
    val face = CelebrityCharacter.of(figureKey)?.face
    if (face == null) {
        CelebrityFigure(modifier = modifier, size = size, figureKey = figureKey)
    } else {
        Image(
            painter = painterResource(face),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .testTag(CELEBRITY_FIGURE_TAG)
                .clearAndSetSemantics {}
        )
    }
}

/** 셀럽 그림의 시험 표지. */
const val CELEBRITY_FIGURE_TAG = "celebrity-figure"

private const val CELEBRITY_FIGURE_ALPHA = 0.6f
