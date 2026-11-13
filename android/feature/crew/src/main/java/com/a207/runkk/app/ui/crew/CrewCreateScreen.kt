package com.a207.runkk.app.ui.crew

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkSegmented
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 크루 만들기(피그마 F-04). 로고는 사진 고르기(PickVisualMedia)로 받아 JPEG로 줄여 올린다. */
@Composable
fun CrewCreateRoute(viewModel: CrewCreateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // 크루 탭을 새로 열면 내 크루를 다시 확인해 방금 만든 크루 홈을 보인다.
                CrewEvent.Created -> navigator.selectTab(Routes.CREW)
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { runCatching { CrewLogoEncoder.fromUri(context, uri) }.getOrNull() }
            if (jpeg == null) viewModel.onLogoUnreadable() else viewModel.onLogoPicked(jpeg)
        }
    }
    val loadLogo = remember(viewModel) { viewModel::logo }
    CompositionLocalProvider(LocalCrewLogoLoader provides loadLogo) {
        CrewCreateScreen(
            state,
            onBack = navigator::back,
            onForm = viewModel::onForm,
            onSubmit = viewModel::onSubmit,
            onPickLogo = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        )
    }
}

/**
 * 크루 만들기(피그마 F-04): 가운데 제목, 로고 칸(66, 점선, +)과 크루명(2~12자), 한 줄 소개(최대 40자), 태그(최대 3개와 안내,
 * 평일·주말 시간대 칩), 공개 여부·가입 방식 2탭, 아래 "크루 만들기"(Lime Large).
 */
@Composable
fun CrewCreateScreen(
    state: CrewCreateUiState,
    onBack: () -> Unit,
    onForm: (CrewFormState) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onPickLogo: () -> Unit = {}
) {
    val form = state.form
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.crew_create_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoSlot(state.logo, isUploading = state.isUploadingLogo, onPick = onPickLogo)
                RunkkTextField(
                    value = form.name,
                    onValueChange = { onForm(form.copy(name = it)) },
                    label = stringResource(R.string.crew_form_name_label),
                    placeholder = stringResource(R.string.crew_create_name_placeholder),
                    maxLength = CrewFormState.NAME_MAX,
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkTextField(
                value = form.intro,
                onValueChange = { onForm(form.copy(intro = it)) },
                label = stringResource(R.string.crew_create_intro_label),
                placeholder = stringResource(R.string.crew_create_intro_placeholder),
                maxLength = CrewFormState.INTRO_MAX
            )
            TagSection(form, onForm)
            FormSegment(
                label = stringResource(R.string.crew_form_visibility_group),
                options = listOf(
                    stringResource(R.string.crew_form_visibility_public),
                    stringResource(R.string.crew_form_visibility_private)
                ),
                selectedIndex = form.visibility.ordinal,
                onSelect = { onForm(form.copy(visibility = CrewVisibility.entries[it])) }
            )
            FormSegment(
                label = stringResource(R.string.crew_form_join_policy_group),
                options = CREATE_JOIN_POLICIES.map { stringResource(it.longLabelRes) },
                selectedIndex = CREATE_JOIN_POLICIES.indexOf(form.joinPolicy),
                onSelect = { onForm(form.copy(joinPolicy = CREATE_JOIN_POLICIES[it])) }
            )
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        RunkkButton(
            stringResource(R.string.crew_create_title),
            onClick = onSubmit,
            isEnabled = state.canSubmit,
            isLoading = state.isSaving,
            // E2E(android/e2e/maestro) 선택자: 상단 "크루 만들기"와 이름이 같아 태그로 찾는다.
            modifier = Modifier
                .testTag("crew_create_submit")
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        )
    }
}

/** 가입 방식 선택지 순서(피그마 F-04): 바로 가입, 승인 후 가입. */
private val CREATE_JOIN_POLICIES: List<JoinPolicy> = listOf(JoinPolicy.FREE, JoinPolicy.APPROVAL)

/**
 * 로고 칸(피그마 F-04): 66x66, surfaceHigh 채움, divider 점선 1, 모서리 24, 가운데 + 28. 고르면 그 그림을 채운다. 누르면 사진 고르기.
 */
@Composable
private fun LogoSlot(logo: ByteArray?, isUploading: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val border = RunkkColors.Border
    val bitmap = remember(logo) {
        logo?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    val label = stringResource(if (logo == null) R.string.crew_create_logo_add else R.string.crew_create_logo_change)
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(shape)
            .background(RunkkColors.SurfaceHigh)
            .drawBehind {
                drawRoundRect(
                    color = border,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                )
            }
            .clickable(enabled = !isUploading, onClickLabel = label, role = Role.Button, onClick = onPick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(66.dp))
        } else {
            Icon(
                RunkkIcons.Plus,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** 태그 머리(피그마 F-04): "태그"(13 Medium) + " 최대 3개"(13), 안내(12, 보조색), 시간대 칩 줄(이름 Regular, 줄 간격 8). */
@Composable
private fun TagSection(form: CrewFormState, onForm: (CrewFormState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = stringResource(R.string.crew_create_tags_title)
            val max = stringResource(R.string.crew_create_tags_max, RunkkTags.MAX_SELECTED)
            Text(
                buildAnnotatedString {
                    withStyle(RunkkTextStyles.text(13, FontWeight.Medium).toSpanStyle()) { append(title) }
                    append(" ")
                    append(max)
                },
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary
            )
            Text(
                stringResource(R.string.crew_create_tags_hint),
                style = RunkkTextStyles.text(12, lineHeight = 16),
                color = RunkkColors.TextSecondary
            )
        }
        CrewTagRows(
            selected = form.tags,
            onToggle = { onForm(form.copy(tags = RunkkTags.toggle(form.tags, it))) },
            isLabelBold = false,
            rowGap = 8
        )
    }
}

/** 라벨(13 Medium) + 2탭 세그먼트(피그마 RunKK / Control, 고른 칸 흰색). */
@Composable
private fun FormSegment(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium, lineHeight = 16),
            color = RunkkColors.TextPrimary
        )
        RunkkSegmented(options, selectedIndex = selectedIndex, onSelect = onSelect, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 크루 로고를 올리기 전에 줄인다: 긴 변 512px, JPEG, 1MB 이하(서버 PhotoUpload 상한). 화질을 낮춰도 넘으면 서버가 거부한다.
 * 휴대폰 사진은 픽셀을 눕힌 채 EXIF 방향 태그만 붙이고 BitmapFactory는 태그를 보지 않으므로, 태그대로 돌리고 뒤집은 뒤 인코딩한다
 * (JPEG로 다시 쓰면 태그가 사라진다).
 */
internal object CrewLogoEncoder {
    private const val MAX_SIDE = 512
    private const val MAX_BYTES = 1_000_000
    private const val START_QUALITY = 90
    private const val QUALITY_STEP = 10
    private const val MIN_QUALITY = 40
    private const val DEGREES_90 = 90
    private const val DEGREES_180 = 180
    private const val DEGREES_270 = 270

    fun fromUri(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded =
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
        val bitmap = upright(decoded, turnOf(orientationOf(context, uri)))
        if (bitmap !== decoded) decoded.recycle()
        return encode(bitmap).also { bitmap.recycle() }
    }

    /** EXIF 방향을 바로 세우는 변환: 시계 방향 [degrees]만큼 돌린 뒤 [isMirrored]면 좌우로 뒤집는다. */
    data class Turn(val degrees: Int, val isMirrored: Boolean)

    /** EXIF 방향 태그 값 → 변환. 모르는 값(0 UNDEFINED 포함)은 그대로 둔다. */
    fun turnOf(orientation: Int): Turn = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Turn(0, true)
        ExifInterface.ORIENTATION_ROTATE_180 -> Turn(DEGREES_180, false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Turn(DEGREES_180, true)
        ExifInterface.ORIENTATION_TRANSPOSE -> Turn(DEGREES_90, true)
        ExifInterface.ORIENTATION_ROTATE_90 -> Turn(DEGREES_90, false)
        ExifInterface.ORIENTATION_TRANSVERSE -> Turn(DEGREES_270, true)
        ExifInterface.ORIENTATION_ROTATE_270 -> Turn(DEGREES_270, false)
        else -> Turn(0, false)
    }

    /** 사진의 EXIF 방향. 읽지 못하면(태그 없음, PNG, 읽기 실패) 그대로(NORMAL)다. */
    private fun orientationOf(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun upright(source: Bitmap, turn: Turn): Bitmap {
        if (turn.degrees == 0 && !turn.isMirrored) return source
        val matrix = Matrix().apply {
            setRotate(turn.degrees.toFloat())
            if (turn.isMirrored) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun encode(source: Bitmap): ByteArray {
        val longest = max(source.width, source.height)
        val scaled = if (longest > MAX_SIDE) {
            val ratio = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).roundToInt().coerceAtLeast(1),
                (source.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        var quality = START_QUALITY
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= QUALITY_STEP
        } while (bytes.size > MAX_BYTES && quality >= MIN_QUALITY)
        if (scaled !== source) scaled.recycle()
        return bytes
    }
}
