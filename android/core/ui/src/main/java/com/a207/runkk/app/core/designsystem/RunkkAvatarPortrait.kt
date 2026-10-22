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
 * - "default:<DefaultCharacter>": 앱에 든 기본 캐릭터 그림(avatar_character_*.xml, 서버 DefaultCharacter 4종과 같다).
 * - "selfie:…": 서버 부위 PNG(FaceImageSource가 받은 층, [RunkkAvatarFace]).
 * - 그 밖(없음·모르는 값): 부르는 쪽의 자리 그림.
 */
object RunkkAvatarArt {
    private const val DEFAULT_PREFIX = "default:"

    /** 서버 DefaultCharacter 이름 → 그림. */
    val characters: Map<String, Int> = mapOf(
        "RABBIT" to R.drawable.avatar_character_rabbit,
        "BEAR" to R.drawable.avatar_character_bear,
        "FOX" to R.drawable.avatar_character_fox,
        "CAT" to R.drawable.avatar_character_cat
    )

    /** 기본 캐릭터 키면 그 그림, 아니면 null. */
    @DrawableRes
    fun characterOf(faceKey: String?): Int? =
        faceKey?.takeIf { it.startsWith(DEFAULT_PREFIX) }?.removePrefix(DEFAULT_PREFIX)?.let(characters::get)

    /** 기본 캐릭터 이름의 얼굴 키(서버 DefaultFace.renderKey와 같은 형식). */
    fun defaultKey(characterName: String): String = DEFAULT_PREFIX + characterName
}

/**
 * 아바타 얼굴 한 칸(홈 상단·우리 집, 마이 프로필 원, 아바타 화면 머리): 기본 캐릭터면 그 그림, 셀카 얼굴이고 부위 그림을 받았으면
 * 겹친 얼굴, 아니면 [placeholder]. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun RunkkAvatarPortrait(
    faceKey: String?,
    faceLayers: List<FaceLayer>,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {}
) {
    val character = RunkkAvatarArt.characterOf(faceKey)
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            character != null -> Image(
                painterResource(character),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag(
                    runkkAvatarCharacterTag(faceKey.orEmpty().substringAfter(':'))
                ),
                contentScale = ContentScale.Fit
            )

            faceLayers.isNotEmpty() -> RunkkAvatarFace(
                faceLayers,
                Modifier.fillMaxSize().testTag(RUNKK_AVATAR_FACE_TAG),
                fallback = placeholder
            )

            else -> placeholder()
        }
    }
}

/** 기본 캐릭터 그림(시험이 찾는 표지). */
fun runkkAvatarCharacterTag(characterName: String): String = "runkk-avatar-character-$characterName"

/** 셀카 얼굴 부위 그림(시험이 찾는 표지). */
const val RUNKK_AVATAR_FACE_TAG = "runkk-avatar-face"
