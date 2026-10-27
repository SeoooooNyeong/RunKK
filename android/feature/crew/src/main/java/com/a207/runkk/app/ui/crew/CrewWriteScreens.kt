package com.a207.runkk.app.ui.crew

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.PollMethod
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- 투표 만들기(FR-206, 와이어프레임 M2) ----------

@Composable
fun CrewPollWriteRoute(key: Routes.CrewPollWrite, viewModel: CrewPollWriteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(state.isPosted) { if (state.isPosted) navigator.back() }
    CrewPollWriteScreen(
        state = state,
        onBack = navigator::back,
        onQuestion = viewModel::onQuestion,
        onMethod = viewModel::onMethod,
        onOption = viewModel::onOption,
        onAddOption = viewModel::onAddOption,
        onRemoveOption = viewModel::onRemoveOption,
        onDay = viewModel::onDay,
        onTime = viewModel::onTime,
        onSubmit = { viewModel.onSubmit(key.crewId) }
    )
}

/** 투표 방식 선택지(와이어프레임 순서: 찬반, 선택지). */
private val POLL_METHODS = listOf(PollMethod.YES_NO, PollMethod.CHOICES)

/** 투표 만들기: 질문, 방식(찬반·선택지), 선택지 2~5개(칸마다 ×, 선택지 추가), 마감 날짜·시각, 올리기. */
@Composable
fun CrewPollWriteScreen(
    state: CrewPollWriteUiState,
    onBack: () -> Unit,
    onQuestion: (String) -> Unit,
    onMethod: (PollMethod) -> Unit,
    onOption: (Int, String) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (Int) -> Unit,
    onDay: (Int) -> Unit,
    onTime: (LocalTime) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalContext.current.resources
    WriteScaffold(
        state = WriteScaffoldState(
            title = stringResource(R.string.crew_poll_write_title),
            submitLabel = stringResource(R.string.crew_poll_write_submit),
            isSubmitEnabled = state.isValid,
            isSaving = state.isSaving,
            error = state.error
        ),
        onBack = onBack,
        onSubmit = onSubmit,
        modifier = modifier
    ) {
        RunkkTextField(
            value = state.question,
            onValueChange = onQuestion,
            label = stringResource(R.string.crew_poll_write_question_label),
            isSingleLine = false
        )
        RunkkSegmentRow(
            options = listOf(
                stringResource(R.string.crew_poll_method_yes_no),
                stringResource(R.string.crew_poll_method_choices)
            ),
            selectedIndex = POLL_METHODS.indexOf(state.method),
            onSelect = { onMethod(POLL_METHODS[it]) },
            groupLabel = stringResource(R.string.crew_poll_write_method_label)
        )
        if (state.method == PollMethod.CHOICES) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel(stringResource(R.string.crew_poll_write_options_label), Modifier.weight(1f))
                    Text(
                        stringResource(R.string.crew_poll_write_options_range),
                        style = RunkkTextStyles.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RunkkTextField(
                            value = option,
                            onValueChange = { onOption(index, it) },
                            label = stringResource(R.string.crew_poll_write_option_label, index + 1),
                            modifier = Modifier.weight(1f)
                        )
                        if (state.canRemoveOption) {
                            RunkkIconButton(
                                RunkkIcons.Close,
                                contentDescription = stringResource(
                                    R.string.crew_poll_write_option_remove_description,
                                    index + 1
                                ),
                                onClick = { onRemoveOption(index) }
                            )
                        }
                    }
                }
                if (state.canAddOption) {
                    RunkkPillButton(
                        stringResource(R.string.crew_poll_write_option_add),
                        onClick = onAddOption,
                        leadingIcon = RunkkIcons.Plus
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.crew_poll_write_deadline_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkSelectField(
                    label = stringResource(R.string.crew_poll_write_date_label),
                    options = (0 until CrewPollWriteUiState.DAYS).toList(),
                    selected = state.dayOffset,
                    optionLabel = { offset -> dayLabel(resources, state.today, offset) },
                    onSelect = onDay,
                    modifier = Modifier.weight(1f)
                )
                RunkkSelectField(
                    label = stringResource(R.string.crew_poll_write_time_label),
                    options = CrewRunWriteUiState.TIMES,
                    selected = state.time,
                    optionLabel = { it.format(TIME) },
                    onSelect = onTime,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------- 게시글 쓰기(FR-211, 와이어프레임 M5) ----------

@Composable
fun CrewBoardWriteRoute(key: Routes.CrewBoardWrite, viewModel: CrewBoardWriteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    LaunchedEffect(state.isPosted) { if (state.isPosted) navigator.back() }
    // 사진 고르기(시스템 사진 선택기, 권한 없이 고른 사진만 읽는다). 고른 뒤 10MB 넘거나 JPEG·PNG가 아닌 사진을 걸러 알린다.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CrewBoardWriteUiState.PHOTO_MAX)
    ) { uris ->
        val accepted = uris.filter { isAcceptablePhoto(context, it) }
        viewModel.onPhotosPicked(accepted.map(Uri::toString), rejectedCount = uris.size - accepted.size)
    }
    CrewBoardWriteScreen(
        state = state,
        onBack = navigator::back,
        onBody = viewModel::onBody,
        onPickPhotos = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemovePhoto = viewModel::onRemovePhoto,
        onSubmit = { viewModel.onSubmit(key.crewId) },
        onMessageShown = viewModel::onMessageShown
    )
}

/** 원본 형식(JPEG·PNG)과 크기(10MB 이하)를 고를 때 미리 본다(FR-211, TC-7440·TC-7441). 서버·압축기도 다시 검사한다. */
private fun isAcceptablePhoto(context: Context, uri: Uri): Boolean {
    val type = context.contentResolver.getType(uri)
    if (type != null && type !in ACCEPTED_TYPES) return false
    val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
    return size == null || size in 1..CrewBoardWriteUiState.PHOTO_BYTES_MAX
}

private val ACCEPTED_TYPES = setOf("image/jpeg", "image/png")

/** 게시글 쓰기: 내용과 글자 수, "사진 n/5장", 3열 격자(추가 칸 + 고른 사진 미리보기와 빼기), 올리기. */
@Composable
fun CrewBoardWriteScreen(
    state: CrewBoardWriteUiState,
    onBack: () -> Unit,
    onBody: (String) -> Unit,
    onPickPhotos: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageShown: () -> Unit = {}
) {
    WriteScaffold(
        state = WriteScaffoldState(
            title = stringResource(R.string.crew_board_write_title),
            submitLabel = stringResource(R.string.crew_board_write_submit),
            isSubmitEnabled = state.canSubmit,
            isSaving = state.isSaving,
            error = state.error,
            message = state.message
        ),
        onBack = onBack,
        onSubmit = onSubmit,
        modifier = modifier,
        onMessageShown = onMessageShown
    ) {
        RunkkTextField(
            value = state.body,
            onValueChange = onBody,
            label = stringResource(R.string.crew_board_write_body_label),
            supporting = countLabel(state.body.codePointLength(), CrewBoardWriteUiState.BODY_MAX),
            // 상한은 코드 포인트로 ViewModel이 자른다(D-143). 입력칸 maxLength는 UTF-16 길이라 쓰지 않는다.
            isSingleLine = false
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel(stringResource(R.string.crew_board_write_photos_label), Modifier.weight(1f))
                Text(
                    stringResource(
                        R.string.crew_board_write_photo_count,
                        state.photos.size,
                        CrewBoardWriteUiState.PHOTO_MAX
                    ),
                    style = RunkkTextStyles.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val cells = listOf<String?>(null) + state.photos
            cells.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { uri ->
                        val cell = Modifier.weight(1f).aspectRatio(1f)
                        if (uri == null) {
                            AddPhotoCell(state.canAddPhoto, onPickPhotos, cell)
                        } else {
                            PickedPhotoCell(uri, state.photos.indexOf(uri) + 1, onRemovePhoto, cell)
                        }
                    }
                    repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 사진 추가 칸(+ 추가). 5장이면 꺼진다. */
@Composable
private fun AddPhotoCell(isEnabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.crew_board_write_add_photo)
    Column(
        modifier
            .clip(RunkkShapes.Small)
            .background(colors.surface)
            .border(1.dp, colors.outline, RunkkShapes.Small)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val tint = if (isEnabled) colors.onSurface else colors.onSurfaceVariant
        Icon(RunkkIcons.Plus, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.crew_board_write_add_photo_short), style = RunkkTextStyles.Caption, color = tint)
    }
}

/** 고른 사진 미리보기(기기 주소를 줄여 푼다)와 오른쪽 위 빼기 단추. */
@Composable
private fun PickedPhotoCell(uri: String, number: Int, onRemove: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            // 읽을 수 없는 주소(권한 만료, 지운 사진)는 미리보기 없이 번호만 보인다.
            runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } }.getOrNull()
        }?.let { bytes -> withContext(Dispatchers.Default) { decodeOrNull(bytes, THUMB_PX) } }
    }
    Box(
        modifier.clip(
            RunkkShapes.Small
        ).background(colors.surface).border(1.dp, colors.outlineVariant, RunkkShapes.Small)
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                stringResource(R.string.crew_board_write_photo_item, number),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).clearAndSetSemantics {}
            )
        }
        RunkkIconButton(
            RunkkIcons.Close,
            contentDescription = stringResource(R.string.crew_board_write_remove_photo_description, number),
            onClick = { onRemove(uri) },
            visualSize = 28.dp,
            iconSize = 14.dp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/** 입력 묶음 머리말(와이어프레임 lb). */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = RunkkTextStyles.text(13, FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/** "0/1,000"(상한은 천 단위 쉼표). */
@Composable
internal fun countLabel(count: Int, max: Int): String = stringResource(R.string.crew_count_grouped, count, max)

/** 글쓰기 화면 틀: 위 바, 내용(스크롤), 오류 문구, 아래 올리기 단추, 안내 줄. */
@Composable
internal fun WriteScaffold(
    state: WriteScaffoldState,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageShown: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val (title, submitLabel, isSubmitEnabled, isSaving, error, message) = state
    val snackbar = remember { SnackbarHostState() }
    val text = message?.asString()
    LaunchedEffect(message) {
        text?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = title, onBack = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
                error?.let {
                    Text(
                        it.asString(),
                        style = RunkkTextStyles.text(13, FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RunkkPrimaryButton(
                submitLabel,
                onClick = onSubmit,
                isEnabled = isSubmitEnabled,
                isLoading = isSaving,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}
