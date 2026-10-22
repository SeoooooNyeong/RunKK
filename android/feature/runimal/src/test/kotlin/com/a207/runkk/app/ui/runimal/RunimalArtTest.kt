package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
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
}

/** 16유형 + 기본 그림을 한 장에 모은 격자(그림체 확인용 기준 이미지). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunimalArtGridScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun allTypes() = compose.captureRunkkScreen("E1_runimal_art_grid") {
        FlowRow(
            modifier = Modifier.fillMaxSize().background(RunkkColors.Background).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 4
        ) {
            RunimalArt.entries.forEach { art ->
                Column(
                    modifier = Modifier
                        .width(87.dp)
                        .clip(RunkkShapes.Medium)
                        .background(RunkkColors.Surface)
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(art.drawableRes), contentDescription = null, modifier = Modifier.size(80.dp))
                    Text(
                        stringResource(art.nameRes),
                        style = RunkkTextStyles.text(10),
                        color = RunkkColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
