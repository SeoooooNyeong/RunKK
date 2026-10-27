package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.PostList
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 게시판 글 한 건(와이어프레임 E3). 공지·투표는 꼬리표와 제목 한 줄, 응원·게시글은 아바타·이름·꼬리표·시각과 본문. 오른쪽 ⋯ 메뉴에
 * 신고(남의 글), 삭제(canDelete, FR-213), 운영진은 공지 고정·해제(FR-205)가 있다.
 */
@Composable
internal fun BoardPostCard(post: PostList.Item, state: CrewBoardUiState, actions: CrewBoardActions) {
    CrewCard {
        when (post.kind) {
            PostList.Kind.NOTICE -> NoticePost(post, state, actions)
            PostList.Kind.POLL -> PollPost(post, state, actions)
            else -> AuthorPost(post, state, actions)
        }
    }
}

@Composable
private fun NoticePost(post: PostList.Item, state: CrewBoardUiState, actions: CrewBoardActions) {
    val isPinned = post.notice?.isPinned == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KindTag(
            stringResource(if (isPinned) R.string.crew_board_pinned_tag else R.string.crew_kind_notice),
            isFilled = isPinned
        )
        Text(
            post.summary,
            style = RunkkTextStyles.BodyStrong,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        PostMenu(post, state, actions)
    }
    Text(
        stringResource(R.string.crew_board_author_time, post.authorName, relativeLabel(post.writtenAt, state.now)),
        style = RunkkTextStyles.Caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PollPost(post: PostList.Item, state: CrewBoardUiState, actions: CrewBoardActions) {
    val colors = MaterialTheme.colorScheme
    val poll = post.poll
    val navigator = LocalRunkkNavigator.current
    val navigate = actions.onNavigate ?: navigator::navigate
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KindTag(stringResource(R.string.crew_kind_poll))
        Text(
            post.summary,
            style = RunkkTextStyles.BodyStrong,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        PostMenu(post, state, actions)
    }
    if (poll == null) return
    val headcount = state.headcount
    if (headcount != null && headcount > 0) {
        RunkkProgressBar(
            poll.responseCount.toFloat() / headcount,
            height = 6.dp,
            color = colors.primary,
            trackColor = colors.outlineVariant
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pollMeta(poll.responseCount, headcount, poll.isClosed, deadlineLabel(poll.closesAt)),
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        val canVote = !poll.isClosed && poll.myOptionNo == null
        BoardButton(
            stringResource(if (canVote) R.string.crew_board_poll_vote else R.string.crew_board_poll_result),
            { navigate(Routes.CrewPoll(state.crewId, post.postId)) },
            isPrimary = canVote
        )
    }
}

/** "응답 4/6명, 10.25 (토) 21:00 마감". 인원을 모르면 "응답 4명, …", 마감됐으면 "응답 4명, 마감". */
@Composable
private fun pollMeta(responses: Int, headcount: Int?, isClosed: Boolean, deadline: String): String = when {
    isClosed -> stringResource(R.string.crew_board_poll_meta_closed, responses)
    headcount != null -> stringResource(R.string.crew_board_poll_meta, responses, headcount, deadline)
    else -> stringResource(R.string.crew_board_poll_meta_no_headcount, responses, deadline)
}

@Composable
private fun AuthorPost(post: PostList.Item, state: CrewBoardUiState, actions: CrewBoardActions) {
    val colors = MaterialTheme.colorScheme
    val author = if (post.isMine) stringResource(R.string.crew_me) else post.authorName
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AuthorAvatar(post.authorName)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(author, style = RunkkTextStyles.BodyStrong, color = colors.onSurface)
                KindTag(stringResource(post.kind.kindLabelRes()))
            }
            Text(
                relativeLabel(post.writtenAt, state.now),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        }
        PostMenu(post, state, actions)
    }
    val body = post.board?.body ?: post.summary
    if (body.isNotBlank()) Text(body, style = RunkkTextStyles.Body, color = colors.onSurface)
    post.board?.photos?.takeIf {
        it.isNotEmpty()
    }?.let { photos -> PhotoPreview(post, photos.map { it.photoId }, actions) }
}

/** 게시글 사진 미리보기: 3칸 한 줄. 4장 이상이면 셋째 칸에 남은 장 수("+2")를 얹는다. 누르면 크게 본다. */
@Composable
private fun PhotoPreview(post: PostList.Item, photoIds: List<Long>, actions: CrewBoardActions) {
    val shown = photoIds.take(COLUMNS)
    val extra = photoIds.size - shown.size
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEachIndexed { index, id ->
            Box(Modifier.weight(1f).aspectRatio(1f)) {
                CrewPhoto(
                    photoId = id,
                    load = actions.loadPhoto,
                    description = stringResource(R.string.crew_board_photo_description, post.authorName, index + 1) +
                        if (index == shown.lastIndex &&
                            extra > 0
                        ) {
                            stringResource(R.string.crew_board_more_photos_description, extra)
                        } else {
                            ""
                        },
                    onClick = { actions.onOpenPhoto(id) },
                    modifier = Modifier.fillMaxSize()
                )
                if (index == shown.lastIndex && extra > 0) {
                    Box(
                        Modifier.fillMaxSize().clip(RunkkShapes.Small).background(Color.Black.copy(alpha = SCRIM_ALPHA))
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.crew_board_more_photos, extra),
                            style = RunkkTextStyles.BodyStrong,
                            color = Color.White
                        )
                    }
                }
            }
        }
        repeat(COLUMNS - shown.size) { Box(Modifier.weight(1f)) }
    }
}

/** ⋯ 메뉴: 신고(남의 글), 삭제(canDelete), 공지 고정·해제(운영진). */
@Composable
private fun PostMenu(post: PostList.Item, state: CrewBoardUiState, actions: CrewBoardActions) {
    val isPinned = post.notice?.isPinned == true
    val entries = listOfNotNull(
        MenuEntry(stringResource(R.string.crew_report)) { actions.onReport(post) }.takeIf { !post.isMine },
        MenuEntry(stringResource(if (isPinned) R.string.crew_notice_unpin else R.string.crew_notice_pin)) {
            actions.onPinToggle(post)
        }.takeIf { post.kind == PostList.Kind.NOTICE && state.canManage },
        MenuEntry(stringResource(R.string.crew_action_delete)) { actions.onDeleteAsk(post) }.takeIf { post.canDelete }
    )
    MoreMenu(
        stringResource(R.string.crew_board_menu_description, post.authorName, post.summary.take(SNIPPET_CHARS)),
        entries
    )
}

/** "10.25 (토) 21:00". 틀은 문자열 리소스(crew_board_deadline_pattern)다. */
@Composable
internal fun deadlineLabel(at: java.time.Instant): String {
    val pattern = LocalContext.current.resources.getString(R.string.crew_board_deadline_pattern)
    return at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
}

/** ⋯ 단추 이름에 넣을 글 앞부분 글자 수(같은 작성자의 글을 구분한다, WCAG 2.4.4). */
private const val SNIPPET_CHARS = 20

private const val SCRIM_ALPHA = 0.55f
