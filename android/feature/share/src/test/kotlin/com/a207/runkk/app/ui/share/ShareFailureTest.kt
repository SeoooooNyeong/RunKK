package com.a207.runkk.app.ui.share

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** 공유·기기 저장의 실패 갈래(리뷰 3차 N3-5): 예상한 실패는 기록하고 사유 문구로 바꾸며, 프로그래밍 오류는 올린다. */
@RunWith(RobolectricTestRunner::class)
class ShareFailureTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val image: Uri = Uri.parse("content://x/card.png")

    @Test
    fun `shareFailureOrNull은 예상한 여섯 가지 실패만 null로 바꾼다`() {
        val expected =
            listOf(
                SecurityException("권한 없음"),
                IOException("저장 공간 없음"),
                ActivityNotFoundException("받을 앱 없음"),
                IllegalArgumentException("주소 오류"),
                IllegalStateException("상태 오류"),
                UnsupportedOperationException("지원하지 않는 제공자")
            )

        expected.forEach { error -> assertNull(shareFailureOrNull("시험") { throw error }, error.javaClass.simpleName) }
    }

    @Test
    fun `shareFailureOrNull은 성공 값을 그대로 돌려주고 그 밖의 예외는 올린다`() {
        assertEquals(ShareOutcome.Shared, shareFailureOrNull("시험") { ShareOutcome.Shared })
        assertFailsWith<ArithmeticException> { shareFailureOrNull("시험") { throw ArithmeticException() } }
    }

    @Test
    fun `카드 파일 주소를 만들지 못하면 쓰기 실패를 돌려주고 공유 대상을 부르지 않는다`() = runTest {
        val target = CountingTarget()
        val sharer =
            CardSharer(context, AlwaysShared, uriFor = { _, _ -> throw IllegalArgumentException("공유 경로 밖") })

        val outcome = sharer.share(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), CardSide.FRONT, target)

        assertEquals(ShareOutcome.Failed("WRITE_FAILED", R.string.share_card_write_failed), outcome)
        assertEquals(0, target.calls)
    }

    @Test
    fun `공유 시트를 열 앱이 없으면 공유 실패를 돌려준다`() {
        val noActivity =
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent?) = throw ActivityNotFoundException()
            }

        val outcome = SystemShareTarget().share(noActivity, image)

        assertEquals(ShareOutcome.Failed("NO_SHARE_APP", R.string.share_no_share_app), outcome)
    }

    @Test
    fun `사진첩 항목을 만들지 못하면 저장 공간 확인 안내로 실패한다`() {
        Robolectric.buildContentProvider(DenyingMediaProvider::class.java).create(MediaStore.AUTHORITY)

        val outcome = DeviceSaveTarget().share(context, image)

        assertEquals(ShareOutcome.Failed("SAVE_FAILED", R.string.share_save_failed_check), outcome)
    }

    @Test
    fun `원본 이미지를 읽지 못하면 반쯤 만든 사진첩 항목을 지우고 저장 실패를 돌려준다`() {
        val resolver = shadowOf(context.contentResolver)

        val outcome = DeviceSaveTarget().share(context, image)

        assertEquals(ShareOutcome.Failed("SAVE_FAILED", R.string.share_save_failed), outcome)
        assertEquals(1, resolver.insertStatements.size)
        assertEquals(1, resolver.deletedUris.size)
    }

    @Test
    fun `사진첩에 다 쓰면 저장했다고 돌려주고 항목을 지우지 않는다`() {
        val resolver = shadowOf(context.contentResolver)
        resolver.registerInputStream(image, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))

        val outcome = DeviceSaveTarget().share(context, image)

        assertEquals(ShareOutcome.Saved, outcome)
        assertEquals(0, resolver.deletedUris.size)
    }
}

private class CountingTarget : ShareTarget {
    var calls = 0

    override fun share(context: Context, image: Uri): ShareOutcome {
        calls++
        return ShareOutcome.Shared
    }
}

private object AlwaysShared : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = true

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** 사진첩 쓰기 권한이 없는 기기처럼 insert를 거부한다. */
class DenyingMediaProvider : ContentProvider() {
    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw SecurityException("사진첩 쓰기 권한 없음")

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
