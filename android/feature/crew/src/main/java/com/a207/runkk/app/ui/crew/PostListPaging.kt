package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PostList

/** 다음 쪽을 이어 붙인다(이미 받은 글은 빼고, 커서는 새 쪽 것). 앞 쪽이 없으면 새 쪽 그대로. */
internal fun PostList?.append(next: PostList): PostList {
    val current = this ?: return next
    val known = current.posts.map { it.postId }.toSet()
    return PostList(current.posts + next.posts.filterNot { it.postId in known }, next.nextCursor)
}

/**
 * 응답·삭제·고정 뒤 갱신(A4): 첫 쪽부터 다시 읽되 지금 받아 둔 글 수([loaded])만큼 다음 쪽을 이어 읽어, 이어 받았던 뒤쪽이
 * 사라지지 않게 한다. 첫 쪽이 실패하면 그 실패를, 뒤쪽이 실패하면 거기까지 읽은 목록(커서는 그 쪽 것이라 끝까지 내리면 이어 받는다)을 준다.
 */
internal suspend fun CrewRepository.postsCovering(crewId: Long, kind: PostList.Kind?, loaded: Int): Outcome<PostList> {
    var list = when (val first = posts(crewId, kind)) {
        is Outcome.Success -> first.value
        is Outcome.Failure -> return first
    }
    var pages = 1
    while (list.posts.size < loaded && pages < COVER_PAGES_MAX) {
        val cursor = list.nextCursor ?: break
        val next = posts(crewId, kind, cursor) as? Outcome.Success ?: break
        pages++
        val grown = list.append(next.value)
        // 새 글이 없거나 커서가 그대로면(서버 이상) 더 읽지 않는다: 같은 쪽을 끝없이 다시 읽지 않게.
        val isStuck = grown.posts.size == list.posts.size || grown.nextCursor == cursor
        list = grown
        if (isStuck) break
    }
    return Outcome.Success(list)
}

/** 갱신 한 번에 이어 읽는 쪽 수 상한(첫 쪽 포함). 넘으면 거기까지만 보이고 끝까지 내리면 이어 받는다. */
private const val COVER_PAGES_MAX = 10
