package com.a207.runkk.app.api.avatar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.SelfieProgress
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/**
 * 셀카 진행(design-oo 05-30 [임시:D-성-117]~[임시:D-성-119], 11절 4번): 저장소가 기기 사본을 바꿀 때마다 같은 프로세스의 진행이 바뀌고,
 * 접수 시각을 남기며, 보낸 사진은 로그인한 회원 것만 돌려주고, 따라가기는 새 접수 REPLACE·이어 걸기 APPEND_OR_REPLACE·다시 켬 KEEP이다.
 */
@RunWith(RobolectricTestRunner::class)
class SelfieProgressTest {
    private val api = ApiTestServer()
    private val clock = FakeClock(5_000L)
    private val store = MemoryStore()
    private val hub = SelfieProgressHub(store)
    private val repository = AvatarRepositoryImpl(api.retrofit.create(), api.faces, store, hub, clock)

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `접수하면 만드는 중(접수 시각), 실패로 끝나면 실패, 확인하면 없음`() = runTest {
        assertEquals(SelfieProgress.None, repository.selfieProgress().first())
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        repository.submitSelfie(byteArrayOf(1))
        assertEquals(SelfieProgress.Working("job-1", 5_000L), repository.selfieProgress().first())

        api.reply("""{"jobId":"job-1","status":"FAILED","error":{"code":"NO_FACE_DETECTED","message":"x"}}""")
        repository.selfieJob("job-1")
        assertEquals(SelfieProgress.Failed("job-1", "NO_FACE_DETECTED"), repository.selfieProgress().first())

        repository.dismissSelfieResult()
        assertEquals(SelfieProgress.None, repository.selfieProgress().first())
        assertNull(store.failure())
    }

    @Test
    fun `결과를 받으면 성공(바뀐 얼굴 키)이고 기본 캐릭터를 고르면 남긴 작업과 진행을 지운다`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        repository.submitSelfie(byteArrayOf(1))
        api.reply("""{"headId":"h-1","faceImage":"data:image/png;base64,AAAA"}""")
        api.reply("""{"faceKey":"head:7:job-1","bodyShape":"STAGE1","skinTone":"DAYTIME"}""")
        repository.finishSelfie("job-1")
        assertEquals(SelfieProgress.Succeeded("job-1", "head:7:job-1"), repository.selfieProgress().first())

        api.reply("""{"jobId":"job-2","status":"QUEUED"}""", code = 202)
        repository.submitSelfie(byteArrayOf(2))
        api.reply("""{"faceKey":"default:FOX","defaultCharacterId":"FOX","bodyShape":"STAGE1","skinTone":"DAYTIME"}""")
        repository.chooseDefault("FOX")
        assertNull(store.load())
        assertEquals(SelfieProgress.None, repository.selfieProgress().first())
    }
}

/** 기기 사본의 접수 시각(회원별). */
@RunWith(RobolectricTestRunner::class)
class PendingSelfieTimeTest {
    private val tokens = Tokens(jwtOf("7"))
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun store() = PreferencesPendingSelfieStore(context, SelfMember(tokens))

    @Test
    fun `접수 시각을 남기고 같은 작업을 다시 남겨도 앞 시각을 둔다`() {
        store().clear()
        store().save("job-1", 100L)
        store().save("job-1", 900L)
        assertEquals(100L, store().submittedAt())
        store().save("job-2", 900L)
        assertEquals(900L, store().submittedAt())
        store().clear()
        assertNull(store().submittedAt())
    }

    @Test
    fun `보낸 사진은 로그인한 회원 것만 돌려주고 새로 남기면 앞 사진을 지운다(D-성-119)`() = runTest {
        val photo = FilePendingSelfiePhoto(context, SelfMember(tokens))
        photo.clear()
        photo.save(byteArrayOf(1, 2))
        assertTrue(byteArrayOf(1, 2).contentEquals(photo.load()))
        tokens.access = jwtOf("8")
        assertNull(photo.load(), "다른 회원은 앞 회원 사진을 보지 않는다")
        photo.save(byteArrayOf(3))
        tokens.access = jwtOf("7")
        assertNull(photo.load(), "다른 회원이 남기면 앞 사진은 지운다")
        photo.clear()
        assertEquals(0, File(context.filesDir, "selfie").listFiles()?.size ?: 0)
    }

    private class Tokens(var access: String?) : TokenProvider {
        override fun accessToken(): String? = access

        override fun refreshToken(): String? = null

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}

/** 따라가기 정책(고유 작업 하나, 네트워크 조건). */
@RunWith(RobolectricTestRunner::class)
class WorkManagerSelfieJobFollowerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun works(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerSelfieJobFollower.UNIQUE_WORK).get()

    @Test
    fun `새 접수는 앞 작업을 바꾸고, 다시 켬은 걸린 작업을 두며, 이어 걸기는 뒤에 붙인다`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        val follower = WorkManagerSelfieJobFollower(context)
        follower.follow("job-1")
        val first = works().single { !it.state.isFinished }
        assertTrue(first.constraints.requiredNetworkType == androidx.work.NetworkType.CONNECTED)

        follower.resume("job-1")
        assertEquals(listOf(first.id), works().filter { !it.state.isFinished }.map { it.id }, "KEEP")

        follower.continueLater("job-1")
        assertEquals(2, works().count { !it.state.isFinished }, "APPEND_OR_REPLACE는 뒤에 붙는다")

        follower.follow("job-2")
        assertEquals(1, works().count { !it.state.isFinished }, "REPLACE는 앞 사슬을 바꾼다")

        follower.cancel()
        assertTrue(works().all { it.state.isFinished })
    }
}
