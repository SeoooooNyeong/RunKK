package com.a207.runkk.app.ui.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.a207.runkk.app.core.designsystem.RUNKK_AVATAR_FACE_LOADING_TAG
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.SelfFaceTone
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RunnerLook
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 랭킹에서 다른 러너도 그 사람의 모습으로 그린다(S15P31A207-300, PM 2026-11-13): 시상대 전신 정지 그림 칸에 서버 모습(얼굴·의상·체형·
 * 러닝량)을 넘기고, 4위부터 얼굴 원은 그 러너의 얼굴 키로 그리며 그 러너의 러닝량으로 그을린다. 모습이 없으면 지금처럼 성별 기본 모습이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp")
class RankingLookTest {
    @get:Rule
    val compose = createComposeRule()

    private val subjects = mutableListOf<AvatarStillSubject>()
    private val probe: @Composable () -> Unit = {
        LocalSlotAvatarStill.current?.let { subjects += it }
        Box(Modifier.fillMaxSize())
    }

    private val windLook = RunnerLook(
        faceKey = "head:12:abc",
        topKey = "top_crew_tee",
        bottomKey = null,
        shoesKey = "shoes_lime_runner",
        bodyVariant = 2,
        runAmount = 37
    )
    private val me = Ranking.Entry(2, "나", Distance(58_000), Gender.MALE, look = windLook.copy(faceKey = "head:7:me"))
    private val ranking = Ranking(
        entries = listOf(
            Ranking.Entry(1, "바람", Distance(62_400), Gender.FEMALE, look = windLook),
            me,
            Ranking.Entry(3, "새벽러너", Distance(51_700), Gender.FEMALE),
            Ranking.Entry(
                4,
                "한강토끼",
                Distance(44_200),
                Gender.MALE,
                look = RunnerLook("head:4:a", null, null, null, 0, 60)
            )
        ),
        mine = me,
        gapToNext = Distance(4_400),
        aggregatedAt = null,
        notice = null
    )

    private fun show(state: RankingUiState, onFaceShown: (String) -> Unit = {}) = compose.setContent {
        CompositionLocalProvider(LocalSlots provides mapOf(SlotKey.AVATAR_STILL to listOf(probe))) {
            RunkkTheme(reduceMotion = true) { RankingScreen(state, {}, {}, {}, onFaceShown = onFaceShown) }
        }
    }

    @Test
    fun `시상대 다른 러너는 랭킹 모습을 전신 정지 그림에 넘기고, 나는 내 저장소 모습, 모습이 없으면 성별 기본이다`() {
        show(RankingUiState(isLoading = false, ranking = ranking))
        compose.waitForIdle()

        val wind = subjects.last { !it.isMine && it.isFemale == true && it.look != null }
        assertEquals(
            AvatarStillSubject.Look(
                faceKey = "head:12:abc",
                topKey = "top_crew_tee",
                bottomKey = null,
                shoesKey = "shoes_lime_runner",
                bodyVariant = 2,
                runAmount = 37
            ),
            wind.look
        )
        assertTrue(subjects.any { it.isMine && it.look == null }, "나는 isMine으로 내 모습(저장소)을 쓴다")
        assertTrue(subjects.any { !it.isMine && it.isFemale == true && it.look == null }, "모습이 없으면 성별 기본")
    }

    @Test
    fun `4위부터 얼굴 원은 화면에 보이면 그 러너의 얼굴 키로 얼굴을 받게 한다`() {
        val shown = mutableListOf<String>()
        show(RankingUiState(isLoading = false, ranking = ranking), onFaceShown = { shown += it })
        compose.waitForIdle()

        assertEquals(listOf("head:4:a"), shown.distinct(), "보이는 줄의 얼굴만 받는다(시상대·내 카드는 받지 않는다)")
    }

    @Test
    fun `화면 밖 줄의 얼굴은 받지 않는다(목록 최대 100줄)`() {
        val many = (4..60).map { rank ->
            Ranking.Entry(
                rank,
                "러너$rank",
                Distance(40_000L - rank * 100),
                look = RunnerLook("head:$rank:x", null, null, null, 0, 0)
            )
        }
        val shown = mutableListOf<String>()
        show(
            RankingUiState(isLoading = false, ranking = ranking.copy(entries = ranking.entries.take(3) + many)),
            onFaceShown = { shown += it }
        )
        compose.waitForIdle()

        assertTrue("head:4:x" in shown, "첫 줄은 보인다: $shown")
        assertTrue("head:60:x" !in shown, "맨 아래 줄은 화면 밖이라 받지 않는다")
    }

    @Test
    fun `모르는 접두어 얼굴 키 줄은 받지 않고 빈 자리 대신 성별 기본 얼굴이다(AR-성-87)`() {
        val future = ranking.entries[3].copy(look = RunnerLook("future:4:a", null, null, null, 0, 60))
        val shown = mutableListOf<String>()
        show(
            RankingUiState(isLoading = false, ranking = ranking.copy(entries = ranking.entries.take(3) + future)),
            onFaceShown = { shown += it }
        )
        compose.waitForIdle()

        assertTrue(shown.isEmpty(), "받을 수 없는 키는 받게 하지 않는다: $shown")
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `얼굴 받기에 실패한 줄은 빈 자리 대신 성별 기본 얼굴이다(AR-성-88)`() {
        show(RankingUiState(isLoading = false, ranking = ranking, faces = mapOf("head:4:a" to emptyList())))
        compose.waitForIdle()

        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `얼굴 원 그을림 - 다른 러너는 그 러너의 얼굴과 러닝량, 나와 모습 없는 줄은 앱 전체 내 톤 그대로다`() {
        val other = ranking.entries[3]
        assertEquals(
            SelfFaceTone("head:4:a", 60, includesDefaultFace = true),
            rankingFaceTone(other, isMine = false)
        )
        assertNull(rankingFaceTone(me, isMine = true))
        assertNull(rankingFaceTone(ranking.entries[2], isMine = false))
    }

    @Test
    fun `얼굴 원 그을림 - 기본 캐릭터 얼굴인 다른 러너도 그 러너의 러닝량으로 피부를 그을린다(PM 2026-11-14)`() {
        val bear = ranking.entries[3].copy(look = RunnerLook("default:BEAR", null, null, null, 0, 60))
        val tone = assertNotNull(rankingFaceTone(bear, isMine = false))
        assertEquals(SelfFaceTone("default:BEAR", 60, includesDefaultFace = true), tone)
        assertNotNull(tone.skinFilterFor("default:BEAR"), "그 줄의 기본 얼굴 원을 그을린다")
        assertNull(tone.skinFilterFor("default:FOX"), "다른 키 얼굴은 그대로")
    }

    @Test
    fun `얼굴 원 그을림 - 내 줄이 기본 캐릭터 얼굴이면 앱 전체 내 러닝량으로 그 원만 그을린다`() {
        val mineDefault = me.copy(look = windLook.copy(faceKey = "default:CAT"))
        val myTone = SelfFaceTone("default:CAT", 42)
        assertEquals(
            SelfFaceTone("default:CAT", 42, includesDefaultFace = true),
            rankingFaceTone(mineDefault, isMine = true, myTone = myTone)
        )
        // 셀카 얼굴인 나는 지금처럼 앱 전체 내 톤 그대로(감싸지 않는다).
        assertNull(rankingFaceTone(me, isMine = true, myTone = SelfFaceTone("head:7:me", 42)))
    }

    @Test
    fun `얼굴 원 그을림 - 모습은 있지만 얼굴을 안 고른 다른 러너는 그을릴 얼굴이 없다(기초 경로 B4)`() {
        val faceless = Ranking.Entry(
            5,
            "무얼굴",
            Distance(40_000),
            Gender.MALE,
            look = RunnerLook(null, null, null, null, 0, 80)
        )
        assertNull(rankingFaceTone(faceless, isMine = false))
    }
}
