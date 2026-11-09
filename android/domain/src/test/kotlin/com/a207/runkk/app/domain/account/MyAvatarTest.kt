package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.testing.CallLog
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MyAvatarTest {
    private class Accounts(private val summary: Outcome<MySummary>, private val layers: Outcome<List<FaceLayer>>) :
        AccountRepository by FakeAccountRepository(CallLog()) {
        val faceCalls = mutableListOf<String?>()

        override suspend fun mySummary(): Outcome<MySummary> = summary

        override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> {
            faceCalls += faceKey
            return layers
        }
    }

    private val layer = FaceLayer(FacePart.HAIR, byteArrayOf(1, 2))

    @Test
    fun `본인 요약의 얼굴 키·배경 키와 그 얼굴의 부위 그림을 모은다(홈과 같은 출처)`() = runTest {
        val accounts = Accounts(
            Outcome.Success(MySummary("새벽곰", "GOLD", 3, "face-7", "bg-park", 12)),
            Outcome.Success(listOf(layer))
        )
        assertEquals(MyAvatar("face-7", listOf(layer), "bg-park"), accounts.myAvatar())
        assertEquals(listOf<String?>("face-7"), accounts.faceCalls)
    }

    @Test
    fun `얼굴 키가 없으면 부위 그림을 읽지 않고, 부위 그림을 못 받으면 빈 목록이다`() = runTest {
        val noFace =
            Accounts(Outcome.Success(MySummary("새벽곰", null, null, null, null, null)), Outcome.Success(emptyList()))
        assertEquals(MyAvatar(null, emptyList(), null), noFace.myAvatar())
        assertEquals(emptyList(), noFace.faceCalls)
        val failed = Accounts(
            Outcome.Success(MySummary("새벽곰", null, null, "face-7", null, null)),
            Outcome.Failure(AppError.Network)
        )
        assertEquals(MyAvatar("face-7", emptyList(), null), failed.myAvatar())
    }

    @Test
    fun `본인 요약을 읽지 못하면 null이다`() = runTest {
        assertNull(Accounts(Outcome.Failure(AppError.Network), Outcome.Success(emptyList())).myAvatar())
    }
}
