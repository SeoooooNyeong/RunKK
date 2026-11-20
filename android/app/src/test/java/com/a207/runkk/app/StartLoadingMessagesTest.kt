package com.a207.runkk.app

import com.a207.runkk.app.core.designsystem.StartLoadingMessage
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.sync.PreloadProgress
import com.a207.runkk.app.domain.sync.PreloadStage
import com.a207.runkk.app.domain.sync.PreloadState
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** 시작 로딩 문구 고르기 규칙(S15P31A207-345 2차): 재시도 > 목록 만들기 > 90% 이상 > 남은 것이 가장 많은 단계. */
class StartLoadingMessagesTest {
    private fun loading(done: Int, total: Int, vararg remaining: Pair<PreloadStage, Int>, isRetrying: Boolean = false) =
        PreloadState.Loading(PreloadProgress(done, total), remaining.toMap(), isRetrying)

    @Test
    fun `지금 받고 있는 일의 문구를 고른다(차례를 기다리는 단계는 남은 파일이 많아도 고르지 않는다)`() {
        // 8회차 관찰: 랭킹 칸 11개 가운데 러너 머리 10칸이 남아 「랭킹」이 4~5초 남았지만 실제로는 내 머리를 받고 있었다
        val state = PreloadState.Loading(
            PreloadProgress(5, 30),
            remaining = mapOf(PreloadStage.RANKING to 10, PreloadStage.MY_AVATAR_FETCH to 7),
            active = mapOf(PreloadStage.MY_AVATAR_FETCH to 7)
        )
        assertEquals(StartLoadingMessage.MY_AVATAR_FETCH, startLoadingMessageOf(state))
    }

    @Test
    fun `목록을 만드는 동안은 시작 문구`() {
        assertEquals(StartLoadingMessage.START, startLoadingMessageOf(PreloadState.Loading(PreloadProgress.UNKNOWN)))
    }

    @Test
    fun `남은 파일이 가장 많은 단계의 문구를 고른다`() {
        val state = loading(
            3,
            30,
            PreloadStage.MY_AVATAR_FETCH to 4,
            PreloadStage.RANKING to 11,
            PreloadStage.CELEB to 1,
            PreloadStage.OTHERS to 5
        )
        assertEquals(StartLoadingMessage.RANKING, startLoadingMessageOf(state))
        assertEquals(
            StartLoadingMessage.RECORDS_CREW_FRIENDS,
            startLoadingMessageOf(loading(0, 10, PreloadStage.OTHERS to 6, PreloadStage.CELEB to 1))
        )
        assertEquals(StartLoadingMessage.CELEB, startLoadingMessageOf(loading(8, 10, PreloadStage.CELEB to 2)))
        assertEquals(
            StartLoadingMessage.MY_AVATAR_PREPARE,
            startLoadingMessageOf(loading(5, 10, PreloadStage.MY_AVATAR_PREPARE to 2, PreloadStage.OTHERS to 1))
        )
    }

    @Test
    fun `같으면 내 아바타부터 앞 단계 문구`() {
        assertEquals(
            StartLoadingMessage.MY_AVATAR_FETCH,
            startLoadingMessageOf(loading(0, 14, PreloadStage.RANKING to 7, PreloadStage.MY_AVATAR_FETCH to 7))
        )
    }

    @Test
    fun `90퍼센트 이상이면 거의 끝났다는 문구`() {
        assertEquals(StartLoadingMessage.ALMOST_DONE, startLoadingMessageOf(loading(9, 10, PreloadStage.RANKING to 1)))
        assertEquals(StartLoadingMessage.ALMOST_DONE, startLoadingMessageOf(loading(5, 10)), "남은 것이 없으면")
    }

    @Test
    fun `자동 재시도 중이면 재시도 문구가 먼저다`() {
        assertEquals(
            StartLoadingMessage.RETRYING,
            startLoadingMessageOf(loading(9, 10, PreloadStage.RANKING to 1, isRetrying = true))
        )
    }

    @Test
    fun `준비 끝이나 실패 화면에는 문구가 없다`() {
        assertNull(startLoadingMessageOf(PreloadState.Ready(PreloadProgress(7, 7))))
        assertNull(startLoadingMessageOf(PreloadState.Failed(PreloadProgress(3, 7), AppError.Network)))
    }
}
