package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.JoinPolicy

/** 크루 탭: 크루 찾기·상세·초대 코드·만들기. */
@Composable
fun CrewFindRoute(viewModel: CrewFindViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CrewEvent.Open -> navigator.navigate(event.key)
            }
        }
    }
    CrewFindScreen(
        state = state,
        actions = CrewFindActions(
            onKeyword = viewModel::onKeyword,
            onSearch = viewModel::load,
            onTag = viewModel::onTagToggle,
            onOpen = viewModel::onOpen,
            onCloseDetail = viewModel::onCloseDetail,
            onJoin = viewModel::onJoin,
            onCreate = { navigator.navigate(Routes.CREW_CREATE) },
            onInviteOpen = viewModel::onInviteOpen,
            onInviteClose = viewModel::onInviteClose,
            onInviteCode = viewModel::onInviteCode,
            onInviteSubmit = viewModel::onInviteSubmit,
            onRetry = viewModel::load,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/** 크루 찾기 화면의 입력 묶음. */
data class CrewFindActions(
    val onKeyword: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onTag: (String) -> Unit = {},
    val onOpen: (CrewList.Item) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onJoin: (CrewList.Item) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onInviteOpen: () -> Unit = {},
    val onInviteClose: () -> Unit = {},
    val onInviteCode: (String) -> Unit = {},
    val onInviteSubmit: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewFindScreen(state: CrewFindUiState, actions: CrewFindActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "크루",
                        style = RunkkTextStyles.Headline,
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    RunkkPillButton("초대 코드", onClick = actions.onInviteOpen)
                    CrewLimePill("+ 만들기", onClick = actions.onCreate)
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunkkTextField(
                        value = state.keyword,
                        onValueChange = actions.onKeyword,
                        label = "크루 찾기",
                        placeholder = "크루명·한 줄 소개 검색",
                        modifier = Modifier.weight(1f)
                    )
                    RunkkPillButton("검색", onClick = actions.onSearch)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RunkkTags.categories.flatMap { it.tags }, key = { it.id }) { tag ->
                        RunkkChoiceChip(tag.label, selected = tag.id in state.tags, onSelectedChange = {
                            actions.onTag(tag.id)
                        })
                    }
                }
            }
            state.recommendations.firstOrNull()?.let { top ->
                item { ForYouCard(top, onOpen = { actions.onOpen(top) }, onJoin = { actions.onJoin(top) }) }
            }
            item {
                Text("공개 크루 · 최신순", style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.TextPrimary)
            }
            when {
                state.loading && state.crews.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp)) { RunkkLoading() }
                }

                state.error != null && state.crews.isEmpty() -> item {
                    RunkkErrorState(state.error, onRetry = actions.onRetry)
                }

                state.crews.isEmpty() -> item {
                    RunkkEmptyState(
                        "조건에 맞는 공개 크루가 없어요",
                        message = "태그를 줄이거나 직접 크루를 만들어 보세요",
                        actionLabel = "크루 만들기",
                        onAction = actions.onCreate
                    )
                }

                else -> items(state.crews, key = {
                    it.crewId
                }) { crew -> CrewRow(crew, onClick = { actions.onOpen(crew) }) }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    state.detail?.let { crew ->
        ModalBottomSheet(onDismissRequest = actions.onCloseDetail, containerColor = RunkkColors.Surface) {
            CrewDetailContent(crew, joining = state.joining, onJoin = { actions.onJoin(crew) })
        }
    }
    if (state.inviteOpen) InviteDialog(state, actions)
}

@Composable
private fun ForYouCard(crew: CrewList.Item, onOpen: () -> Unit, onJoin: () -> Unit) {
    RunkkCard(
        shape = RunkkShapes.ExtraLarge,
        borderColor = RunkkColors.Lime,
        onClick = onOpen,
        onClickLabel = "${crew.name} 자세히 보기",
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "FOR YOU · 성향이 맞는 크루",
            style = RunkkTextStyles.display(14, letterSpacingEm = 0.08f),
            color = RunkkColors.Lime
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(crew.name, size = 56, accent = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    crew.name,
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(crewMeta(crew, leader = false), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
            CrewLimePill(
                if (crew.joinPolicy == JoinPolicy.FREE) "가입" else "신청",
                onClick = onJoin,
                enabled = crew.headcount < CREW_MAX
            )
        }
    }
}

@Composable
private fun CrewRow(crew: CrewList.Item, onClick: () -> Unit) {
    RunkkCard(onClick = onClick, onClickLabel = "${crew.name} 자세히 보기") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(crew.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(crew.name, style = RunkkTextStyles.text(15, FontWeight.Black), color = RunkkColors.TextPrimary)
                Text(crewMeta(crew, leader = true), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                if (crew.tags.isNotEmpty()) {
                    Text(
                        crew.tags.joinToString(" · ") { RunkkTags.label(it) },
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RunkkBadge(crew.joinPolicy.label)
        }
    }
}

/** "크루장 X · 4/10명 · 이번 주 12.3km"(크루 밖 열람 범위, FR-117·W-16). */
internal fun crewMeta(crew: CrewList.Item, leader: Boolean): String = listOfNotNull(
    crew.leaderName.takeIf { leader }?.let { "크루장 $it" },
    if (leader) null else crew.joinPolicy.label,
    "${crew.headcount}/${CREW_MAX}명",
    "이번 주 ${RunkkFormat.kmShort(crew.weeklyDistance)}km"
).joinToString(" · ")

internal const val CREW_MAX = 10

/** 크루 상세(크루 밖 열람 범위: 소개·태그·로고·인원·크루장·이번 주 거리, Q-45). */
@Composable
internal fun CrewDetailContent(crew: CrewList.Item, joining: Boolean, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(
            rememberScrollState()
        ).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(crew.name, size = 56, accent = true)
            Column(Modifier.weight(1f)) {
                Text(
                    crew.name,
                    style = RunkkTextStyles.TopBarTitle,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                Text(crewMeta(crew, leader = true), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
        crew.intro?.let { Text(it, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary) }
        if (crew.tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(crew.tags.toList()) { RunkkBadge(RunkkTags.label(it)) }
            }
        }
        InfoLine("가입 방식", crew.joinPolicy.label)
        HorizontalDivider(color = RunkkColors.Border)
        Text(
            "정원은 크루장 포함 10명이에요. 크루를 나가면 24시간 동안 다른 크루에 가입할 수 없어요.",
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        val full = crew.headcount >= CREW_MAX
        RunkkPrimaryButton(
            text = when {
                full -> "정원이 찼어요"
                crew.joinPolicy == JoinPolicy.FREE -> "바로 가입하기"
                else -> "가입 신청하기"
            },
            onClick = onJoin,
            enabled = !full,
            loading = joining,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InviteDialog(state: CrewFindUiState, actions: CrewFindActions) {
    AlertDialog(
        onDismissRequest = actions.onInviteClose,
        containerColor = RunkkColors.Surface,
        title = { Text("초대 코드로 가입", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary) },
        text = {
            RunkkTextField(
                value = state.inviteCode,
                onValueChange = actions.onInviteCode,
                label = "6자리 영문·숫자 · 승인 없이 바로 가입",
                placeholder = "AB12CD"
            )
        },
        confirmButton = {
            TextButton(
                onClick = actions.onInviteSubmit,
                enabled =
                    state.inviteCode.length == INVITE_LENGTH && !state.joining
            ) {
                Text("가입", style = RunkkTextStyles.BodyStrong, color = RunkkColors.Lime)
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onInviteClose) {
                Text("취소", style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextSecondary)
            }
        }
    )
}

private const val INVITE_LENGTH = 6

/** 크루 만들기. */
@Composable
fun CrewCreateRoute(viewModel: CrewCreateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CrewEvent.Open -> {
                    navigator.back()
                    navigator.navigate(event.key)
                }
            }
        }
    }
    CrewCreateScreen(state, onBack = navigator::back, onForm = viewModel::onForm, onSubmit = viewModel::onSubmit)
}

@Composable
fun CrewCreateScreen(
    state: CrewCreateUiState,
    onBack: () -> Unit,
    onForm: (CrewFormState) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = "크루 만들기", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CrewFormFields(state.form, onForm)
            Text(
                "정원은 크루장 포함 최대 10명이에요. 만든 사람이 크루장이 돼요.",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            state.error?.let {
                Text(it, style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.Lavender)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        RunkkPrimaryButton(
            "크루 만들기",
            onClick = onSubmit,
            enabled = state.form.valid,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

@Preview(name = "크루 찾기", widthDp = 390, heightDp = 844)
@Composable
private fun CrewFindPreview() {
    RunkkTheme(reduceMotion = true) { CrewFindScreen(CrewFindUiState(loading = false), CrewFindActions()) }
}
