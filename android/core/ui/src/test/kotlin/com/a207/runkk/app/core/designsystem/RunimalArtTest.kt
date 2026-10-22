package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.ContextCompat
import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 러니멀 유형 값 → 동물 그림·이름 대응표. 서버 RunimalType 16종은 android/contract/enums/RunimalType.txt로 확인한다. */
@RunWith(RobolectricTestRunner::class)
class RunimalArtTest {
    private val context = RuntimeEnvironment.getApplication()
    private val serverTypes = ContractEnums.names("RunimalType")

    @Test
    fun `서버 16유형이 모두 대응표에 있고 기본 그림으로 빠지지 않는다`() {
        assertEquals(16, serverTypes.size)
        serverTypes.forEach { assertTrue(RunimalArt.of(it) != RunimalArt.UNKNOWN, "대응표에 없는 유형: $it") }
        assertEquals(serverTypes, RunimalArt.entries.filter { it != RunimalArt.UNKNOWN }.map { it.name }.toSet())
    }

    @Test
    fun `모든 그림이 실제 drawable로 읽히고 정사각이다`() {
        RunimalArt.entries.forEach { art ->
            val drawable = assertNotNull(ContextCompat.getDrawable(context, art.drawableRes), art.name)
            assertTrue(drawable.intrinsicWidth > 0, art.name)
            assertEquals(drawable.intrinsicWidth, drawable.intrinsicHeight, art.name)
        }
    }

    @Test
    fun `16유형의 이름은 서버 표시 이름과 같고 서로 다르다`() {
        val names = serverTypes.map { context.getString(RunimalArt.of(it).nameRes) }
        assertEquals("아침 참새", context.getString(RunimalArt.MORNING_SPARROW.nameRes))
        assertEquals("들판의 야생마", context.getString(RunimalArt.WILD_HORSE.nameRes))
        assertEquals(16, names.toSet().size)
    }

    @Test
    fun `모르는 값·null·표시 이름은 기본 그림이다`() {
        assertEquals(RunimalArt.UNKNOWN, RunimalArt.of(null))
        assertEquals(RunimalArt.UNKNOWN, RunimalArt.of(""))
        assertEquals(RunimalArt.UNKNOWN, RunimalArt.of("CHEETAH"))
        assertEquals(RunimalArt.UNKNOWN, RunimalArt.of("UNKNOWN"))
        assertEquals(RunimalArt.UNKNOWN, RunimalArt.of("morning_sparrow"))
        assertEquals(R.drawable.runimal_unknown, RunimalArt.of("CHEETAH").drawableRes)
    }

    @Test
    fun `서버 4축이 모두 한국어 이름으로 바뀌고 영문 이름은 쓰지 않는다`() {
        val axes = listOf("COMPANION", "DISTANCE", "COURSE", "HABIT")
        assertEquals(axes.toSet(), RunimalAxisName.entries.map { it.name }.toSet())
        val labels = axes.map { context.getString(assertNotNull(RunimalAxisName.of(it)).nameRes) }
        assertEquals(listOf("동행", "거리", "코스", "습관"), labels)
        assertEquals(null, RunimalAxisName.of("PACE"))
        assertEquals(null, RunimalAxisName.of(null))
    }
}

/** 작은 유형 표시(프로필·크루원 목록)는 enum 이름 대신 한국어 이름을 보인다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunimalTypeLabelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `모든 유형이 한국어 이름으로 보이고 enum 이름은 화면에 없다`() {
        val types = ContractEnums.names("RunimalType").toList() + listOf("CHEETAH", "")
        var index by mutableIntStateOf(0)
        compose.setContent { RunimalTypeLabel(types[index]) }
        types.indices.forEach { i ->
            compose.runOnIdle { index = i }
            val art = RunimalArt.of(types[i])
            val shown = RuntimeEnvironment.getApplication().getString(art.nameRes)
            compose.onNodeWithText(shown).assertExists()
            if (types[i].isNotEmpty()) compose.onNodeWithText(types[i]).assertDoesNotExist()
        }
    }

    @Test
    fun `모르는 값은 알 수 없는 유형으로 보인다`() {
        compose.setContent { RunimalTypeLabel("CHEETAH") }
        compose.onNodeWithText("알 수 없는 유형").assertExists()
        compose.onNodeWithText("CHEETAH").assertDoesNotExist()
    }
}
