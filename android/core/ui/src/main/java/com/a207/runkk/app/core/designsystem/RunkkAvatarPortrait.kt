package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 아바타 얼굴 키(서버 Face.renderKey, 설계 6.1.8 avatar)를 그림으로 바꾼다. 그리는 쪽은 얼굴 종류를 묻지 않고 키만 넘긴다.
 * - "default:<DefaultCharacter>": 어느 기본 캐릭터 값이든 표시 성별의 기본 머리 그림이다([임시:D-성-180], [임시:D-성-187]). 동물 그림은
 *   그리지 않는다. 서버 값은 그대로 받는다.
 * - "selfie:…"·"head:…": 서버 부위 PNG(FaceImageSource가 받은 층, [RunkkAvatarFace]). 받기 전·받지 못하면 기본 머리다.
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
 * 받았으면 겹친 얼굴, 그 밖(얼굴 없음, 기본 캐릭터 값, 셀카 그림을 아직 받지 못함·깨짐)은 [character]의 기본 머리다([임시:D-성-180]).
 * [character]는 내 그림이면 기본값(내 성별), 다른 러너면 그 러너의 표시 성별을 넘긴다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun RunkkAvatarPortrait(
    faceKey: String?,
    faceLayers: List<FaceLayer>,
    modifier: Modifier = Modifier,
    character: AvatarCharacter = LocalAvatarCharacter.current
) {
    val isSelfie = faceKey != null && !RunkkAvatarArt.isDefaultKey(faceKey) && faceLayers.isNotEmpty()
    Box(modifier, contentAlignment = Alignment.Center) {
        if (isSelfie) {
            RunkkAvatarFace(faceLayers, Modifier.fillMaxSize().testTag(RUNKK_AVATAR_FACE_TAG)) {
                DefaultHead(character)
            }
        } else {
            DefaultHead(character)
        }
    }
}

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

/** 셀카 얼굴 부위 그림(시험이 찾는 표지). */
const val RUNKK_AVATAR_FACE_TAG = "runkk-avatar-face"
