package com.a207.runkk.app.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarPortrait
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkCheckRow
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 얼굴 만들기(J7, 가입에서 옮김, FR-155~FR-158): 셀카(카메라·앨범)로 SD 얼굴을 만들거나 기본 캐릭터 얼굴을 골라 저장한다.
 * 셀카는 줄여 보낸 뒤 원본을 남기지 않는다(SelfieEncoder). 서버가 얼굴을 만드는 동안 「만드는 중」을 보이고, 끝나면 이전 화면(꾸미기)으로.
 */
@Composable
fun AvatarSetupRoute(viewModel: AvatarSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AvatarSetupEvent.Done -> navigator.back()
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap == null) viewModel.onSelfieUnavailable() else viewModel.onSelfieCaptured(bitmap)
    }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { decodeSampled(context, uri) }.getOrNull() }
            if (bitmap == null) viewModel.onSelfieUnavailable() else viewModel.onSelfieCaptured(bitmap)
        }
    }
    AvatarSetupScreen(
        state = state,
        onBack = navigator::back,
        onSelfie = { if (viewModel.onSelfieRequested()) camera.launch(null) },
        onAlbum = {
            if (viewModel.onSelfieRequested()) {
                album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        onFaceConsent = viewModel::onFaceConsent,
        onCharacter = viewModel::onCharacterSelected,
        onSave = viewModel::onConfirmCharacter
    )
}

/** 앨범 사진을 긴 변이 셀카 한도(1024px) 근처가 되게 줄여 읽는다(큰 사진을 통째로 메모리에 올리지 않는다). */
private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SelfieEncoder.MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

@Composable
fun AvatarSetupScreen(
    state: AvatarSetupUiState,
    onBack: () -> Unit,
    onSelfie: () -> Unit,
    onCharacter: (DefaultCharacter) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onAlbum: () -> Unit = {},
    onFaceConsent: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(
            title = stringResource(R.string.avatar_setup_title),
            onBack = onBack,
            containerColor = RunkkColors.Surface,
            hasDivider = true
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.avatar_setup_headline),
                style = RunkkTextStyles.text(20, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            FacePreview(state)
            SelfieCard(state, onSelfie, onAlbum, onFaceConsent)
            Text(
                stringResource(R.string.avatar_setup_pick_character),
                style = RunkkTextStyles.Label,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            CharacterRow(state.selected, onCharacter)
            if (state.error != null) {
                Text(
                    state.error.asString(),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        RunkkButton(
            stringResource(R.string.avatar_setup_save),
            onClick = onSave,
            isEnabled = state.selected != null,
            isLoading = state.isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .background(RunkkColors.Surface)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        )
    }
}

/**
 * 아바타 얼굴 미리보기: 고른 기본 캐릭터, 없으면 지금 얼굴. 서버가 셀카 얼굴을 만드는 중이면 얼굴 둘레에 진행 원을 돌리고
 * 「얼굴을 만들고 있어요」를 알린다(S15P31A207-248).
 */
@Composable
private fun FacePreview(state: AvatarSetupUiState) {
    val key =
        state.selected?.let { RunkkAvatarArt.defaultKey(it.name) } ?: state.faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY
    // 피그마 H-1: 흰 카드(높이 200, 모서리 20) 가운데 146x176 세로 자리(surfaceHigh, 모서리 16)에 얼굴을 보인다.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Box(
            Modifier
                .size(width = 146.dp, height = 176.dp)
                .clip(RunkkShapes.Medium)
                .background(RunkkColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            RunkkAvatarPortrait(
                key,
                faceLayers = emptyList(),
                modifier = Modifier.size(112.dp).clip(CircleShape)
            )
            if (state.isMakingFace) {
                CircularProgressIndicator(
                    color = RunkkColors.LimeFill,
                    trackColor = RunkkColors.Border,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(124.dp)
                )
            }
        }
        if (state.isMakingFace) {
            Text(
                stringResource(R.string.avatar_setup_making),
                style = RunkkTextStyles.text(14, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

/**
 * 셀카로 만들기 카드(하루 1번, 피그마 H-1): 얼굴 변환 서비스 전송 동의 체크, 셀카 찍기, 앨범에서 고르기. 오늘 만들었으면 두 버튼을
 * 끈다. 동의는 체크하면 바로 저장하고, 이미 동의했으면 체크된 채 둔다(철회는 마이 > 동의 관리).
 */
@Composable
private fun SelfieCard(
    state: AvatarSetupUiState,
    onSelfie: () -> Unit,
    onAlbum: () -> Unit,
    onFaceConsent: () -> Unit
) {
    val canCreateSelfie = state.canCreateSelfie
    val isWorking = state.isWorking || state.isMakingFace
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.avatar_setup_selfie_title),
                style = RunkkTextStyles.Label,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.avatar_setup_selfie_daily),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RunkkColors.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        RunkkCheckRow(
            label = stringResource(R.string.avatar_setup_face_consent),
            isChecked = state.hasFaceConsent,
            onCheckedChange = { isChecked -> if (isChecked) onFaceConsent() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RunkkButton(
                stringResource(R.string.avatar_setup_take_selfie),
                onClick = onSelfie,
                isEnabled = canCreateSelfie && !isWorking,
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
            RunkkButton(
                stringResource(R.string.avatar_setup_pick_album),
                onClick = onAlbum,
                isEnabled = canCreateSelfie && !isWorking,
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 기본 캐릭터 얼굴 4종을 한 줄로. */
@Composable
private fun CharacterRow(selected: DefaultCharacter?, onCharacter: (DefaultCharacter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DefaultCharacter.entries.forEachIndexed { index, character ->
            CharacterOption(
                character,
                number = index + 1,
                isSelected = character == selected,
                onClick = { onCharacter(character) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CharacterOption(
    character: DefaultCharacter,
    number: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = stringResource(character.labelRes)
    val pickLabel = stringResource(R.string.avatar_setup_pick_label, name)
    // 피그마 H-1: 76.5x80 칸, 그림 아래 "기본 n"(Medium 10). 고른 칸은 surfaceHigh 바탕에 primary 2 테두리다.
    Column(
        modifier = modifier
            .height(80.dp)
            .clip(RunkkShapes.Medium)
            .background(if (isSelected) RunkkColors.SurfaceHigh else RunkkColors.Surface)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) RunkkColors.LimeFill else RunkkColors.Border,
                RunkkShapes.Medium
            )
            .clickable(role = Role.RadioButton, onClickLabel = pickLabel, onClick = onClick)
            .semantics {
                this.selected = isSelected
                contentDescription = name
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RunkkAvatarPortrait(
            RunkkAvatarArt.defaultKey(character.name),
            faceLayers = emptyList(),
            modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
        ) {
            Text(name, style = RunkkTextStyles.text(14, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
        Text(
            stringResource(R.string.avatar_setup_character_label, number),
            style = RunkkTextStyles.text(10, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

@Preview(name = "J7 얼굴 만들기", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarSetupPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarSetupScreen(AvatarSetupUiState(isLoading = false, selected = DefaultCharacter.FOX), {}, {}, {}, {})
    }
}
