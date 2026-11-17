package com.a207.runkk.app.ui.crew

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkSegmented
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy

/**
 * 크루 정보 수정(피그마 F-26, 크루장): 로고(연필 덮개)와 크루명 한 줄, 한 줄 소개, 태그(최대 3개, 32 칩), 공개 여부·가입 방식 세그먼트,
 * 아래 라임 "저장"(위 구분선 없음). 로고 연필 덮개를 누르면 사진 고르기를 열고, 고른 사진은 크루 만들기처럼 올려 저장 때 그 주소를 싣는다
 * (DV-6). 로고는 고른 사진 → 지금 로고 → 기본 그림 순으로 보인다.
 */
@Composable
internal fun CrewEditPage(state: CrewHomeUiState, actions: CrewHomeActions) {
    BackHandler { actions.onPage(CrewHomePage.INFO) }
    val form = state.editForm
    val original = state.detail?.name.orEmpty()
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_edit_title), onBack = { actions.onPage(CrewHomePage.INFO) })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EditableLogo(
                    picked = state.editLogo,
                    logoUrl = form.logoUrl,
                    isUploading = state.isUploadingLogo,
                    onPick = actions.onEditLogoPick
                )
                RunkkTextField(
                    value = form.name,
                    onValueChange = { actions.onEditForm(form.copy(name = it)) },
                    label = stringResource(R.string.crew_edit_name),
                    maxLength = editNameMaxLength(original),
                    error = if (form.isEditNameValid(original)) {
                        null
                    } else {
                        stringResource(R.string.crew_edit_name_invalid, CrewFormState.NAME_MIN, CrewFormState.NAME_MAX)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkTextField(
                value = form.intro,
                onValueChange = { actions.onEditForm(form.copy(intro = it)) },
                label = stringResource(R.string.crew_edit_intro),
                maxLength = CrewFormState.INTRO_MAX,
                isSingleLine = false
            )
            FieldLabel(
                stringResource(R.string.crew_edit_tags),
                stringResource(R.string.crew_edit_tags_max, RunkkTags.MAX_SELECTED)
            )
            CrewTagPicker(
                selected = form.tags,
                onToggle = { actions.onEditForm(form.copy(tags = RunkkTags.toggle(form.tags, it))) }
            )
            FieldLabel(stringResource(R.string.crew_edit_visibility))
            RunkkSegmented(
                options = listOf(
                    stringResource(R.string.crew_form_visibility_public),
                    stringResource(R.string.crew_form_visibility_private)
                ),
                selectedIndex = form.visibility.ordinal,
                onSelect = { actions.onEditForm(form.copy(visibility = CrewVisibility.entries[it])) },
                modifier = Modifier.fillMaxWidth()
            )
            FieldLabel(stringResource(R.string.crew_edit_join_policy))
            RunkkSegmented(
                options = EDIT_JOIN_POLICIES.map { stringResource(it.longLabelRes) },
                selectedIndex = EDIT_JOIN_POLICIES.indexOf(form.joinPolicy),
                onSelect = { actions.onEditForm(form.copy(joinPolicy = EDIT_JOIN_POLICIES[it])) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        RunkkButton(
            stringResource(R.string.crew_manage_info_save),
            onClick = actions.onEditSave,
            isEnabled = form.isEditValid(original) && !state.isBusy && !state.isUploadingLogo,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        )
    }
}

/** 가입 방식 선택지 순서(피그마 F-26): 바로 가입, 승인 후 가입. */
private val EDIT_JOIN_POLICIES: List<JoinPolicy> = listOf(JoinPolicy.FREE, JoinPolicy.APPROVAL)

/** 입력 이름(피그마 13 Medium 본문색) + 덧붙임 13 Regular. */
@Composable
private fun FieldLabel(title: String, suffix: String? = null) {
    Text(
        buildAnnotatedString {
            withStyle(RunkkTextStyles.text(13, FontWeight.Medium).toSpanStyle()) { append(title) }
            suffix?.let { withStyle(RunkkTextStyles.Label.toSpanStyle()) { append(it) } }
        },
        color = RunkkColors.TextPrimary,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * 크루 이미지 수정(피그마 `RunKK / Avatar / 크루 이미지 수정` 66): 고른 사진(올리는 중에도) 또는 크루 로고(없으면 기본 그림) 위 검정 50%
 * 덮개와 흰 연필 24. 누르면 사진 고르기(DV-6), 올리는 동안은 누를 수 없다.
 */
@Composable
private fun EditableLogo(picked: ByteArray?, logoUrl: String?, isUploading: Boolean, onPick: () -> Unit) {
    val bitmap = remember(picked) {
        picked?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    val label = stringResource(R.string.crew_edit_logo)
    Box(
        Modifier
            .size(66.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !isUploading, role = Role.Button, onClick = onPick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(66.dp))
        } else {
            CrewImage(logoUrl, size = 66.dp, corner = 24.dp)
        }
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(LOGO_EDIT_SCRIM)
        )
        Icon(PencilIcon, contentDescription = null, tint = RunkkColors.OnSecondary)
    }
}

/**
 * 내 정보 시트(피그마 F-23): 제목 "내 정보" 17 Bold, 한 줄 소개(40자), 나이·성별 공개 토글(계정 프로필 공개 설정을 함께 저장한다,
 * 결정), 라임 "저장". 나이·성별 값은 내 줄(공개 설정과 무관한 내 값)이다.
 */
@Composable
internal fun MyInfoSheet(state: CrewHomeUiState, actions: CrewHomeActions) {
    val me = state.me
    RunkkBottomSheet(onDismissRequest = actions.onIntroDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.crew_home_intro_title),
                style = RunkkTextStyles.text(17, FontWeight.Bold, lineHeight = 20),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            RunkkTextField(
                value = state.introDraft,
                onValueChange = actions.onIntroDraft,
                label = stringResource(
                    R.string.crew_home_intro_label,
                    state.introDraft.length,
                    CrewHomeUiState.INTRO_MAX
                ),
                maxLength = CrewHomeUiState.INTRO_MAX,
                imeAction = ImeAction.Done
            )
            // 공개 설정을 모르면(옛 서버) 토글을 숨긴다(저장도 하지 않는다).
            if (me?.isAgePublic != null && me.isGenderPublic != null) {
                Column {
                    VisibilityRow(
                        title = stringResource(R.string.crew_member_age),
                        value = me.ageBand,
                        isPublic = state.isAgePublic,
                        onChange = actions.onAgePublic
                    )
                    VisibilityRow(
                        title = stringResource(R.string.crew_member_gender),
                        value = me.gender?.let { genderLabel(it) },
                        isPublic = state.isGenderPublic,
                        onChange = actions.onGenderPublic
                    )
                }
            }
            RunkkButton(
                stringResource(R.string.crew_home_intro_save),
                onClick = actions.onIntroSave,
                isEnabled = !state.isBusy,
                isLoading = state.isBusy,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

/** 공개 토글 줄(피그마 F-23): 이름("나이") + 설명("20대, 공개"), 오른쪽 라임 스위치, 아래 divider. */
@Composable
private fun VisibilityRow(title: String, value: String?, isPublic: Boolean, onChange: (Boolean) -> Unit) {
    val state = stringResource(if (isPublic) R.string.crew_my_info_public else R.string.crew_member_private)
    Column(Modifier.fillMaxWidth()) {
        RunkkSwitchRow(
            title = title,
            isChecked = isPublic,
            onCheckedChange = onChange,
            description = value?.let { stringResource(R.string.crew_my_info_age_value, it, state) } ?: state
        )
        HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)
    }
}
