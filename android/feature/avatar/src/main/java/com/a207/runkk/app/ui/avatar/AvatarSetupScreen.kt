package com.a207.runkk.app.ui.avatar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarPortrait
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.shouldAskNotificationPermission
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 얼굴 만들기(피그마 AV-7 사진 업로드 흐름 1안, FR-155~FR-158): 1 고르기 → 2 사진 확인(올린 사진을 크게, 동의, 「만들기 시작」) →
 * 3 만드는 중(보낸 사진, 「나중에 다시 확인해도 돼요」, 다른 화면 둘러보기) → 새 얼굴 / 실패. 화면이 보이는 동안 얼굴 만들기 화면 표시를
 * 열어 두어 결과는 알림 대신 화면으로 알린다(design-oo 05-30 [임시:D-성-120]). 셀카는 줄여 보낸 뒤 보낸 사진 한 장만 결과가 날 때까지
 * 기기에 둔다([임시:D-성-119]).
 */
@Composable
fun AvatarSetupRoute(viewModel: AvatarSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenVisible()
        onStopOrDispose { viewModel.onScreenHidden() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AvatarSetupEvent.Done -> navigator.back()
            }
        }
    }
    // 카메라는 원본(EXIF 방향 포함)을 앱 전용 임시 파일에 받아 앨범과 같이 바로 세우고 줄인 뒤 지운다(실기기 DV3-3, FR-156).
    // 원본은 읽기가 실패하거나 화면을 떠나 취소돼도 지운다(리뷰 RC-2). 사용자가 촬영을 취소하면 문구 없이 고르기에 남는다(RC-3).
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isTaken ->
        val capture = SelfieCapture.fileOf(context)
        if (!isTaken) {
            capture.delete()
            viewModel.onSelfieCancelled()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                SelfieCapture.readThenDelete(capture) { SelfieImage.decode(context, captureUriOf(context, capture)) }
            }
            if (bitmap == null) viewModel.onSelfieUnavailable() else viewModel.onSelfieCaptured(bitmap)
        }
    }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { SelfieImage.decode(context, uri) }
            if (bitmap == null) viewModel.onSelfieUnavailable() else viewModel.onSelfieCaptured(bitmap)
        }
    }
    // 결과 알림을 받으려면 Android 13부터 알림 권한이 있어야 한다. 「만들기 시작」 직전에 이 화면에서 한 번 묻고, 거부해도 보낸다
    // (결과는 화면·홈 배지로 본다, 리뷰 R2-12, [임시:D-성-160]).
    var isNotificationAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onStart() }
    val start = {
        val isGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (shouldAskNotificationPermission(Build.VERSION.SDK_INT, isGranted, isNotificationAsked)) {
            isNotificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onStart()
        }
    }
    val openAlbum = {
        if (viewModel.onSelfieRequested()) {
            SelfieCapture.clear(context)
            album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    AvatarSetupScreen(
        state = state,
        actions = AvatarSetupActions(
            onBack = navigator::back,
            onSelfie = {
                if (viewModel.onSelfieRequested()) camera.launch(captureUriOf(context, SelfieCapture.prepare(context)))
            },
            onAlbum = openAlbum,
            onFaceConsent = viewModel::onFaceConsent,
            onCharacter = viewModel::onCharacterSelected,
            onSave = viewModel::onConfirmCharacter,
            onRepick = viewModel::onRepick,
            onStart = start,
            onLater = navigator::back,
            onDoneConfirm = viewModel::onDoneConfirm,
            onRetry = {
                viewModel.onRetry()
                openAlbum()
            },
            onUseDefault = viewModel::onUseDefault
        )
    )
}

private fun captureUriOf(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, SelfieFileProvider.authority(context.packageName), file)

@Composable
fun AvatarSetupScreen(state: AvatarSetupUiState, actions: AvatarSetupActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        when (state.phase) {
            SetupPhase.CHOOSE -> ChooseStep(state, actions)
            SetupPhase.CONFIRM, SetupPhase.SENDING -> ConfirmStep(state, actions)
            SetupPhase.WORKING -> WorkingStep(state, actions)
            SetupPhase.DONE -> DoneStep(state, actions)
            SetupPhase.FAILED -> FailedStep(state, actions)
        }
    }
}

/** 셀카 고르기 칸의 시험 태그. 셀카 문구가 이 칸 위에 있는지 본다(DV-성-10). */
internal const val SELFIE_CARD_TAG = "avatar_setup_selfie_card"

/** 1 얼굴 만들기(62329:1715): 지금 얼굴 카드(오늘 횟수 칩), 셀카 찍기(라임)·앨범(흰), 기본 얼굴 4종, 고른 기본 얼굴 저장. */
@Composable
private fun ColumnScope.ChooseStep(state: AvatarSetupUiState, actions: AvatarSetupActions) {
    RunkkTopBar(title = stringResource(R.string.avatar_setup_title), onBack = actions.onBack)
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        CurrentFaceCard(state)
        state.error?.let {
            Text(
                it.asString(),
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.Lavender,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.height(16.dp))
        val canPick = state.canCreateSelfie && !state.isWorking && !state.isMakingFace
        Row(
            modifier = Modifier.testTag(SELFIE_CARD_TAG),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PickCard(
                stringResource(R.string.avatar_setup_take_selfie),
                R.drawable.avatar_ic_camera,
                isPrimary = true,
                isEnabled = canPick,
                onClick = actions.onSelfie,
                modifier = Modifier.weight(1f)
            )
            PickCard(
                stringResource(R.string.avatar_setup_pick_album),
                R.drawable.avatar_ic_image,
                isPrimary = false,
                isEnabled = canPick,
                onClick = actions.onAlbum,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.avatar_setup_pick_character),
            style = RunkkTextStyles.text(15, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DefaultCharacter.entries.forEach { character ->
                CharacterOption(character, isSelected = character == state.selected, onClick = {
                    actions.onCharacter(character)
                })
            }
        }
        state.saveError?.let {
            Text(
                it.asString(),
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.Lavender,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    val selected = state.selected
    if (selected != null && RunkkAvatarArt.defaultKey(selected.name) != state.faceKey) {
        BottomBar {
            AvatarPillButton(
                stringResource(R.string.avatar_setup_save),
                onClick = actions.onSave,
                isEnabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 지금 얼굴(흰 카드 358×220, 모서리 24): 가운데 140 동그라미 얼굴, 오른쪽 위 「오늘 1회」 칩(오늘 다 썼으면 「내일 다시」). */
@Composable
private fun CurrentFaceCard(state: AvatarSetupUiState) {
    val shape = RoundedCornerShape(24.dp)
    val key =
        state.selected?.let { RunkkAvatarArt.defaultKey(it.name) } ?: state.faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 23.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(RunkkColors.SurfaceHigh)
        ) {
            RunkkAvatarPortrait(key, faceLayers = emptyList(), modifier = Modifier.fillMaxSize())
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 15.dp, end = 15.dp)
                .clip(CircleShape)
                .background(RunkkColors.SurfaceHigh)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.avatar_ic_sparkle),
                null,
                Modifier.size(16.dp),
                tint = RunkkColors.TextPrimary
            )
            Text(
                stringResource(
                    if (state.canCreateSelfie) R.string.avatar_setup_selfie_daily else R.string.avatar_setup_selfie_used
                ),
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** 사진 고르기 칸(132 높이, 모서리 20, 안쪽 20): 44 동그라미 아이콘 + 16 Bold. 셀카는 라임, 앨범은 흰 카드. */
@Composable
private fun PickCard(
    text: String,
    @DrawableRes icon: Int,
    isPrimary: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .height(132.dp)
            .clip(shape)
            .background(if (isPrimary) RunkkColors.LimeFill else RunkkColors.Surface)
            .then(if (isPrimary) Modifier else Modifier.border(1.dp, RunkkColors.Border, shape))
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isPrimary) Color.White.copy(alpha = ICON_WELL_ALPHA) else RunkkColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                null,
                Modifier.size(24.dp),
                tint = if (isPrimary) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
            )
        }
        Text(
            text,
            style = RunkkTextStyles.text(16, FontWeight.Bold),
            color = if (isPrimary) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
        )
    }
}

/** 기본 얼굴(동그라미 72, 고른 것은 라임 3 테두리). */
@Composable
private fun CharacterOption(character: DefaultCharacter, isSelected: Boolean, onClick: () -> Unit) {
    val name = stringResource(character.labelRes)
    val pickLabel = stringResource(R.string.avatar_setup_pick_label, name)
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(RunkkColors.SurfaceHigh)
            .border(
                if (isSelected) 3.dp else 1.dp,
                if (isSelected) RunkkColors.LimeFill else RunkkColors.Border,
                CircleShape
            )
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        RunkkAvatarPortrait(
            RunkkAvatarArt.defaultKey(character.name),
            faceLayers = emptyList(),
            modifier = Modifier.fillMaxSize().semantics { this.contentDescription = pickLabel }
        ) {
            Text(name, style = RunkkTextStyles.text(14, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
    }
}

/** 2 사진 확인(62329:1747): 올린 사진 358×440(얼굴 맞춤 점선, 다시 고르기), 동의 한 줄, 「만들기 시작」. */
@Composable
private fun ColumnScope.ConfirmStep(state: AvatarSetupUiState, actions: AvatarSetupActions) {
    val isSending = state.phase == SetupPhase.SENDING
    RunkkTopBar(title = stringResource(R.string.avatar_setup_confirm_title), onBack = actions.onRepick)
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(shape)
                .background(RunkkColors.SurfaceHigh)
        ) {
            SentPhoto(state.photo, stringResource(R.string.avatar_setup_sent_photo), Modifier.fillMaxSize())
            FaceGuide(Modifier.align(Alignment.TopCenter).padding(top = 70.dp).size(width = 200.dp, height = 250.dp))
            if (!isSending) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = REPICK_ALPHA))
                        .clickable(role = Role.Button, onClick = actions.onRepick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.avatar_ic_refresh), null, Modifier.size(16.dp), tint = Color.White)
                    Text(
                        stringResource(R.string.avatar_setup_repick),
                        style = RunkkTextStyles.text(13, FontWeight.Medium),
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ConsentRow(state.hasFaceConsent, isEnabled = !isSending, onClick = actions.onFaceConsent)
        state.error?.let {
            Text(
                it.asString(),
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.Lavender,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    BottomBar {
        AvatarPillButton(
            stringResource(if (isSending) R.string.avatar_setup_sending else R.string.avatar_setup_start),
            onClick = actions.onStart,
            icon = R.drawable.avatar_ic_sparkle,
            isEnabled = !isSending && state.hasFaceConsent,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 얼굴 맞춤 선(흰 점선 타원 200×250, 선 2). 장식이다. */
@Composable
private fun FaceGuide(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val stroke = GUIDE_STROKE.toPx()
        drawOval(
            Color.White,
            style = Stroke(
                stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(GUIDE_DASH.toPx(), GUIDE_GAP.toPx()))
            )
        )
    }
}

/** 동의 한 줄(흰 카드, 모서리 16): 체크 22(라임) + 「얼굴 변환 서비스로 사진을 보내는 데 동의해요」. 동의했으면 체크된 채 둔다. */
@Composable
private fun ConsentRow(isChecked: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .toggleable(
                value = isChecked,
                enabled = isEnabled,
                role = Role.Checkbox,
                onValueChange = { checked -> if (checked) onClick() }
            ).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val box = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(22.dp)
                .clip(box)
                .background(if (isChecked) RunkkColors.LimeFill else RunkkColors.Surface)
                .then(if (isChecked) Modifier else Modifier.border(1.5.dp, RunkkColors.InputBorder, box)),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    painterResource(R.drawable.avatar_ic_check),
                    null,
                    Modifier.size(16.dp),
                    tint = RunkkColors.OnLimeFill
                )
            }
        }
        Text(
            stringResource(R.string.avatar_setup_face_consent),
            style = RunkkTextStyles.text(14, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 3 만드는 중(62329:1787): 보낸 사진 동그라미 176 둘레 돌아가는 고리 220(서버가 진행률을 주지 않아 %·남은 시간은 보이지 않는다,
 * 피그마 구현 메모 「가짜 % 금지」), 「얼굴을 만드는 중이에요」, 보통 걸리는 시간, 단계 3칸, 「나중에 다시 확인해도 돼요」 상자,
 * 「다른 화면 둘러보기」. 화면을 떠나도 작업은 이어진다.
 */
@Composable
private fun ColumnScope.WorkingStep(state: AvatarSetupUiState, actions: AvatarSetupActions) {
    RunkkTopBar(title = stringResource(R.string.avatar_setup_title), onBack = actions.onLater)
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(54.dp))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                color = RunkkColors.LimeFill,
                trackColor = RunkkColors.SurfaceHigh,
                strokeWidth = 10.dp
            )
            Box(
                Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(RunkkColors.SurfaceHigh)
            ) {
                SentPhoto(state.photo, stringResource(R.string.avatar_setup_sent_photo), Modifier.fillMaxSize()) {
                    RunkkAvatarPortrait(
                        state.faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY,
                        emptyList(),
                        Modifier.fillMaxSize()
                    )
                }
            }
        }
        Spacer(Modifier.height(42.dp))
        Text(
            stringResource(R.string.avatar_setup_making),
            style = RunkkTextStyles.text(22, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.avatar_ic_timer),
                null,
                Modifier.size(16.dp),
                tint = RunkkColors.TextSecondary
            )
            Text(
                stringResource(R.string.avatar_setup_making_eta),
                style = RunkkTextStyles.text(14, FontWeight.Medium),
                color = RunkkColors.TextSecondary
            )
        }
        Spacer(Modifier.height(38.dp))
        StepRow()
        Spacer(Modifier.height(38.dp))
        LaterNotice()
        Spacer(Modifier.height(16.dp))
    }
    BottomBar {
        AvatarPillButton(
            stringResource(R.string.avatar_setup_browse),
            onClick = actions.onLater,
            icon = R.drawable.avatar_ic_home,
            isPrimary = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 단계 3칸: 사진 올림(라임 체크), 얼굴 만들기(보라 모래시계), 적용(회색 3). */
@Composable
private fun StepRow() {
    val description = stringResource(R.string.avatar_setup_steps_description)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepDot(stringResource(R.string.avatar_setup_step_uploaded), RunkkColors.LimeFill, isCurrent = true) {
            Icon(painterResource(R.drawable.avatar_ic_check), null, Modifier.size(16.dp), tint = RunkkColors.OnLimeFill)
        }
        StepDot(stringResource(R.string.avatar_setup_step_making), RunkkColors.Violet, isCurrent = true) {
            Icon(
                painterResource(R.drawable.avatar_ic_hourglass),
                null,
                Modifier.size(16.dp),
                tint = RunkkColors.OnSecondary
            )
        }
        StepDot(stringResource(R.string.avatar_setup_step_apply), RunkkColors.SurfaceHigh, isCurrent = false) {
            Text(STEP_THREE, style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.TextSecondary)
        }
    }
}

@Composable
private fun StepDot(label: String, color: Color, isCurrent: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { content() }
        Text(
            label,
            style = RunkkTextStyles.text(13, if (isCurrent) FontWeight.Medium else FontWeight.Normal),
            color = if (isCurrent) RunkkColors.TextPrimary else RunkkColors.TextSecondary
        )
    }
}

/** 「나중에 다시 확인해도 돼요 / 다 되면 알림으로 알려 드릴게요」 상자(보라 옅은 바탕, 모서리 16, 종 22). */
@Composable
private fun LaterNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.SecondaryContainer)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.avatar_ic_bell), null, Modifier.size(22.dp), tint = RunkkColors.Violet)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.avatar_setup_later_title),
                style = RunkkTextStyles.text(15, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
            Text(
                stringResource(R.string.avatar_setup_later_body),
                style = RunkkTextStyles.text(13),
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 새 얼굴: 무대 358×420에 바뀐 얼굴로 선 아바타, 「새 얼굴이 준비됐어요」, 「확인」. 결과는 워커가 이미 적용했다([임시:D-성-118]). */
@Composable
private fun ColumnScope.DoneStep(state: AvatarSetupUiState, actions: AvatarSetupActions) {
    RunkkTopBar(title = stringResource(R.string.avatar_setup_done_title), onBack = actions.onDoneConfirm)
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val figure = stringResource(R.string.avatar_runner_figure)
        AvatarLookStage(
            look = state.look,
            backgroundKey = state.backgroundKey,
            faceKey = state.faceKey,
            faceLayers = state.faceLayers,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(RunkkColors.SurfaceHigh)
                .semantics {
                    contentDescription = figure
                    role = Role.Image
                }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.avatar_setup_done_headline),
            style = RunkkTextStyles.text(22, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.avatar_setup_done_body),
            style = RunkkTextStyles.text(14, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
    BottomBar {
        AvatarPillButton(
            stringResource(R.string.avatar_setup_done_confirm),
            onClick = actions.onDoneConfirm,
            icon = R.drawable.avatar_ic_check,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 3′ 실패(62329:1969): 보낸 사진(흐림 + 오류 표시), 「얼굴을 만들지 못했어요」와 사유, 「오늘 횟수는 그대로예요」, 기본 얼굴 쓰기·다른 사진. */
@Composable
private fun ColumnScope.FailedStep(state: AvatarSetupUiState, actions: AvatarSetupActions) {
    RunkkTopBar(title = stringResource(R.string.avatar_setup_title), onBack = actions.onBack)
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        FailedPhoto(state)
        Spacer(Modifier.height(44.dp))
        Text(
            stringResource(R.string.avatar_setup_failed_title),
            style = RunkkTextStyles.text(22, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        state.failure?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it.asString(),
                style = RunkkTextStyles.text(14, FontWeight.Medium),
                color = RunkkColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        CountKeptRow(state.canCreateSelfie)
        Spacer(Modifier.height(16.dp))
    }
    BottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarPillButton(
                stringResource(R.string.avatar_setup_use_default),
                onClick = actions.onUseDefault,
                isPrimary = false,
                modifier = Modifier.weight(1f)
            )
            AvatarPillButton(
                stringResource(R.string.avatar_setup_other_photo),
                onClick = actions.onRetry,
                icon = R.drawable.avatar_ic_image,
                isEnabled = state.canCreateSelfie,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 실패한 사진(흐림 + 오른쪽 아래 오류 표시). 사진이 없으면 지금 얼굴. */
@Composable
private fun FailedPhoto(state: AvatarSetupUiState) {
    Box(Modifier.size(176.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(RunkkColors.SurfaceHigh)
                .alpha(FAILED_PHOTO_ALPHA)
        ) {
            SentPhoto(state.photo, stringResource(R.string.avatar_setup_sent_photo), Modifier.fillMaxSize()) {
                RunkkAvatarPortrait(
                    state.faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY,
                    emptyList(),
                    Modifier.fillMaxSize()
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(RunkkColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(RunkkIcons.Alert, null, Modifier.size(28.dp), tint = RunkkColors.Lavender)
        }
    }
}

/** 「오늘 횟수는 그대로예요」 줄(실패는 하루 횟수에 세지 않는다, FR-155). 오늘 더 만들 수 있으면 「1회」. */
@Composable
private fun CountKeptRow(canCreateSelfie: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 17.dp, vertical = 17.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.avatar_ic_sparkle), null, Modifier.size(22.dp), tint = RunkkColors.Violet)
        Text(
            stringResource(R.string.avatar_setup_count_kept),
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (canCreateSelfie) {
            Text(
                stringResource(R.string.avatar_setup_selfie_count),
                style = RunkkTextStyles.text(15, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** 아래 고정 단추 자리(좌우 16, 아래 20). */
@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
        content()
    }
}

/** 보낸 사진(JPEG)을 잘라 채운다. 없거나 풀지 못하면 [fallback]. */
@Composable
private fun SentPhoto(
    jpeg: ByteArray?,
    description: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {}
) {
    val image: ImageBitmap? = remember(jpeg) {
        jpeg?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    if (image == null) {
        Box(modifier) { fallback() }
    } else {
        Image(image, contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

private const val DISABLED_ALPHA = 0.4f
private const val ICON_WELL_ALPHA = 0.5f
private const val REPICK_ALPHA = 0.55f
private const val FAILED_PHOTO_ALPHA = 0.55f
private const val STEP_THREE = "3"
private val GUIDE_STROKE = 2.dp
private val GUIDE_DASH = 10.dp
private val GUIDE_GAP = 8.dp

@Preview(name = "J7 얼굴 만들기 1 고르기", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarSetupPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarSetupScreen(AvatarSetupUiState(isLoading = false, selected = DefaultCharacter.FOX), AvatarSetupActions())
    }
}

@Preview(name = "J7 얼굴 만들기 3 만드는 중", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarSetupWorkingPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarSetupScreen(AvatarSetupUiState(isLoading = false, phase = SetupPhase.WORKING), AvatarSetupActions())
    }
}
