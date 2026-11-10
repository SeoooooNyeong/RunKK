package com.a207.runkk.app.ui.crew

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewList
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 크루 로고 바이트를 읽는 함수(CrewRepository.logo). 화면 Route가 ViewModel의 함수를 넣는다. 없으면(미리보기·스냅샷) 기본 이미지만
 * 그린다.
 */
internal val LocalCrewLogoLoader = staticCompositionLocalOf<(suspend (String) -> Outcome<ByteArray>)?> { null }

/**
 * 크루 이미지(피그마 크루 기본 이미지, S15P31A207-295): 로고가 있으면 그 그림, 없거나 읽지 못하면 surfaceHigh 바탕에 사람들(groups)
 * 아이콘이다. 장식이라 읽지 않는다(크루명은 옆 글자가 읽힌다).
 */
@Composable
internal fun CrewImage(logoUrl: String?, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val loader = LocalCrewLogoLoader.current
    val bitmap by produceState<ImageBitmap?>(null, logoUrl, loader) {
        value = null
        val url = logoUrl ?: return@produceState
        val load = loader ?: return@produceState
        val bytes = (load(url) as? Outcome.Success)?.value ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier.size(size).clip(shape).background(RunkkColors.SurfaceHigh).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(
                RunkkIcons.Group,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(size * DEFAULT_ICON_RATIO)
            )
        }
    }
}

private const val DEFAULT_ICON_RATIO = 0.5f

/**
 * 크루 태그 알약(피그마 Medium 태그, F-01c·F-02·F-03c): 높이 28, 모서리 14, 좌우 14, 14 Medium. 평일은 보라 틴트, 주말은 라임 틴트다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewTagPills(tags: Collection<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkTags.sorted(tags).forEach { id ->
            val weekend = RunkkTags.of(id)?.isWeekend == true
            Box(
                Modifier
                    .heightIn(min = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (weekend) RunkkColors.TagLime else RunkkColors.TagPurple)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    RunkkTags.label(id),
                    style = RunkkTextStyles.text(14, FontWeight.Medium),
                    color = if (weekend) RunkkColors.OnTagLime else RunkkColors.OnTagPurple
                )
            }
        }
    }
}

/** 카드 안 작은 태그 줄(피그마 Outline Small 태그, F-01 카드): 높이 22, 알약, divider 테두리, 11 Regular, 간격 4. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrewTagOutlines(tags: Collection<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RunkkTags.sorted(tags).forEach { id ->
            Box(
                Modifier
                    .heightIn(min = 22.dp)
                    .border(1.dp, RunkkColors.Border, RunkkShapes.Pill)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(RunkkTags.label(id), style = RunkkTextStyles.text(11), color = RunkkColors.TextPrimary)
            }
        }
    }
}

/**
 * 크루 찾기·검색 카드(피그마 F-01·F-01d): surface + divider, 모서리 24, 좌우 14·상하 12. 왼쪽 크루 이미지 48, 가운데 크루명(14 Bold)·
 * 한 줄 소개(13, 한 줄 말줄임)·태그, 오른쪽 인원 "4/10"(18 Bold). 보조 기술에는 크루명·소개·인원을 한 번에 읽고 눌러 상세를 연다.
 */
@Composable
internal fun FindCrewCard(crew: CrewList.Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RunkkShapes.ExtraLarge
    val headcount = stringResource(R.string.crew_meta_headcount, crew.headcount, CREW_MAX)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .clickable(
                onClickLabel = stringResource(R.string.crew_find_open_detail, crew.name),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CrewImage(crew.logoUrl, size = 48.dp, corner = 16.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                crew.name,
                style = RunkkTextStyles.text(14, FontWeight.Bold, lineHeight = 17),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            crew.intro?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = RunkkTextStyles.text(13, lineHeight = 18),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
            }
            if (crew.tags.isNotEmpty()) CrewTagOutlines(crew.tags)
        }
        Text(
            "${crew.headcount}/$CREW_MAX",
            style = RunkkTextStyles.text(18, FontWeight.Bold, lineHeight = 22),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.clearAndSetSemantics { contentDescription = headcount }
        )
    }
}

/** 목록 제목 줄의 새로고침 단추(피그마 F-01: 32 원, divider 테두리, "↻"). 누르는 칸은 48dp다. */
@Composable
internal fun RefreshButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.crew_find_refresh)
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).border(1.dp, RunkkColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("↻", style = RunkkTextStyles.text(16, FontWeight.Bold), color = RunkkColors.TextPrimary)
        }
    }
}

/**
 * 크루 검색 바(피그마 RunKK / Search / 크루 검색 바): 46 알약, surface + divider, 좌우 12, 돋보기 18 + 8. [content]에 자리 문구나
 * 입력칸을 넣는다.
 */
@Composable
internal fun CrewSearchBarFrame(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RunkkShapes.Pill)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Pill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            RunkkIcons.Search,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        content()
    }
}

/** 개설일 "2026. 10. 01"(피그마 F-02 "yyyy. mm. dd", 기기 시간대). */
internal fun createdDateLabel(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    CREATED_FORMAT.format(at.atZone(zone))

private val CREATED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd", Locale.KOREA)

/** 남은 시간 "hh:mm:ss"(24시간 가입 제한 카운트다운, 피그마 F-02c). 0 아래는 00:00:00. */
internal fun countdownLabel(remaining: Duration): String {
    val seconds = remaining.seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
}
