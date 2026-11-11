package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.a207.runkk.app.core.model.FaceLayer
import kotlin.math.roundToInt

/**
 * 아바타 얼굴 키(서버 Face.renderKey, 설계 6.1.8 avatar)를 그림으로 바꾼다. 그리는 쪽은 얼굴 종류를 묻지 않고 키만 넘긴다.
 * - "default:<DefaultCharacter>": 어느 기본 캐릭터 값이든 표시 성별의 기본 머리 그림이다([임시:D-성-180], [임시:D-성-187]). 동물 그림은
 *   그리지 않는다. 서버 값은 그대로 받는다.
 * - "selfie:…"·"head:…": 서버 부위 PNG(FaceImageSource가 받은 층, [RunkkAvatarFace]). 셀카 머리("head:…")는 기본 머리 그림과 같은
 *   구도로 맞추고 목을 원 아래 가장자리까지 잇는다([PortraitHeadFrame], [임시:D-성-189]). 받는 중이면 중립 자리, 받지 못했거나 깨졌으면
 *   기본 머리다([임시:D-성-190]).
 * - 없음: 기본 머리.
 */
object RunkkAvatarArt {
    private const val DEFAULT_PREFIX = "default:"

    /** 기본 캐릭터 키(서버 DefaultFace.renderKey "default:<이름>")인가. 어느 이름이든 기본 얼굴로 그린다. */
    fun isDefaultKey(faceKey: String?): Boolean = faceKey?.startsWith(DEFAULT_PREFIX) == true

    /** 기본 캐릭터 이름의 얼굴 키(서버 DefaultFace.renderKey와 같은 형식). */
    fun defaultKey(characterName: String): String = DEFAULT_PREFIX + characterName

    /** 얼굴을 고르기 전에 쓰는 얼굴이자 「기본 얼굴」을 고를 때 서버에 저장하는 값: 기본 캐릭터 1번(토끼, FR-155·FR-158). */
    val DEFAULT_FACE_KEY: String = defaultKey("RABBIT")

    /**
     * 표시 성별의 기본 머리 그림(512×512, Rive 기본 머리 정면 face + eyes_open + hair를 얼굴 가운데 정사각으로 자른 것). `:core:ui`가
     * `:core:avatar` 자산에 기대지 않게 drawable로 둔다. 내장 머리 그림이 바뀌면 생성 스크립트로 다시 만든다.
     */
    @DrawableRes
    fun defaultHeadOf(character: AvatarCharacter): Int = when (character) {
        AvatarCharacter.MALE -> R.drawable.avatar_default_head_male
        AvatarCharacter.FEMALE -> R.drawable.avatar_default_head_female
    }
}

/**
 * 아바타 얼굴 한 칸(프로필 원: 홈 상단, 마이, 프로필 편집, 다른 러너, 랭킹·크루·대결의 얼굴 원, 얼굴 만들기): 셀카 얼굴이고 부위 그림을
 * 받았으면 겹친 얼굴, 그 밖(얼굴 없음, 기본 캐릭터 값, 셀카 그림을 받지 못함·깨짐)은 [character]의 기본 머리다([임시:D-성-180]).
 * [isFaceLoading]이면(부르는 쪽이 얼굴 키나 셀카 그림을 아직 받는 중) 기본 머리 대신 빈 중립 자리를 둬 기본 머리가 보였다가 셀카로
 * 바뀌지 않게 한다. 기본 캐릭터 키는 기다릴 것이 없어 바로 기본 머리다([임시:D-성-190]).
 * [character]는 내 그림이면 기본값(내 성별), 다른 러너면 그 러너의 표시 성별을 넘긴다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun RunkkAvatarPortrait(
    faceKey: String?,
    faceLayers: List<FaceLayer>,
    modifier: Modifier = Modifier,
    character: AvatarCharacter = LocalAvatarCharacter.current,
    isFaceLoading: Boolean = false
) {
    val isDefault = RunkkAvatarArt.isDefaultKey(faceKey)
    val isSelfie = faceKey != null && !isDefault && faceLayers.isNotEmpty()
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            isSelfie && faceKey.startsWith(HEAD_PREFIX) ->
                SelfieHead(faceLayers, character, Modifier.fillMaxSize().testTag(RUNKK_AVATAR_FACE_TAG)) {
                    DefaultHead(character)
                }

            isSelfie -> RunkkAvatarFace(faceLayers, Modifier.fillMaxSize().testTag(RUNKK_AVATAR_FACE_TAG)) {
                DefaultHead(character)
            }

            // 받는 중: 빈 자리(부르는 쪽 원 바탕만 보인다).
            isFaceLoading && !isDefault -> Box(Modifier.fillMaxSize().testTag(RUNKK_AVATAR_FACE_LOADING_TAG))

            else -> DefaultHead(character)
        }
    }
}

/**
 * 셀카 머리 세 층(얼굴·눈·헤어)을 기본 머리 그림 구도([PortraitHeadFrame])로 그린다. 목 끝이 상자 바닥에 닿지 않으면 목 줄을 바닥까지
 * 이어 그린다(얼굴 층 아래). 구도를 정하지 못하면 캔버스를 상자에 맞춰 그대로 그리고, 그림 하나라도 읽지 못하면 [fallback]이다.
 */
@Composable
private fun SelfieHead(
    layers: List<FaceLayer>,
    character: AvatarCharacter,
    modifier: Modifier,
    fallback: @Composable () -> Unit
) {
    val head = remember(layers, character) { SelfieHeadArt.of(layers, character) }
    if (head == null) {
        fallback()
        return
    }
    Canvas(modifier.clipToBounds()) { drawHead(head) }
}

private fun DrawScope.drawHead(head: SelfieHeadArt) {
    val side = minOf(size.width, size.height)
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    val frame = head.frame
    head.layers.forEach { image ->
        if (frame == null) {
            // 구도를 모르면 캔버스를 상자에 맞춘다(지금까지와 같다).
            val fit = side / maxOf(image.width, image.height)
            drawLayer(image, left + (side - image.width * fit) / 2, top + (side - image.height * fit) / 2, fit)
            return@forEach
        }
        val x = left + frame.dx * side
        val y = top + frame.dy * side
        val scale = frame.scale * side
        val row = frame.neckRow
        if (image === head.face && row != null) drawNeck(image, row, x, y + row * scale, top + side, scale)
        drawLayer(image, x, y, scale)
    }
}

/** 얼굴 층의 목 줄 [row]를 [from]부터 상자 바닥 [to]까지 늘려 그린다(얼굴 층이 그 위를 덮는다). */
private fun DrawScope.drawNeck(face: ImageBitmap, row: Int, x: Float, from: Float, to: Float, scale: Float) {
    val start = from.roundToInt()
    val end = to.roundToInt()
    if (end <= start) return
    drawImage(
        face,
        srcOffset = IntOffset(0, row),
        srcSize = IntSize(face.width, 1),
        dstOffset = IntOffset(x.roundToInt(), start),
        dstSize = IntSize((face.width * scale).roundToInt(), end - start),
        filterQuality = FilterQuality.Medium
    )
}

private fun DrawScope.drawLayer(image: ImageBitmap, x: Float, y: Float, scale: Float) = drawImage(
    image,
    dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
    dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
    filterQuality = FilterQuality.Medium
)

@Composable
private fun DefaultHead(character: AvatarCharacter) {
    Image(
        painterResource(RunkkAvatarArt.defaultHeadOf(character)),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().testTag(runkkAvatarDefaultHeadTag(character)),
        contentScale = ContentScale.Fit
    )
}

/** 기본 머리 그림(시험이 찾는 표지, 성별마다 다르다). */
fun runkkAvatarDefaultHeadTag(character: AvatarCharacter): String = "runkk-avatar-default-head-${character.name}"

/** 셀카 그림을 받는 중의 중립 자리(시험이 찾는 표지). */
const val RUNKK_AVATAR_FACE_LOADING_TAG = "runkk-avatar-face-loading"

/** 셀카 머리 얼굴 키 머리말("head:{회원ID}:{작업ID}"). */
private const val HEAD_PREFIX = "head:"

/** 셀카 얼굴 부위 그림(시험이 찾는 표지). */
const val RUNKK_AVATAR_FACE_TAG = "runkk-avatar-face"
