package com.a207.runkk.app.ui.crew

import androidx.exifinterface.media.ExifInterface
import kotlin.test.Test
import kotlin.test.assertEquals

/** 크루 로고 EXIF 방향(리뷰 M5): 휴대폰 사진의 방향 태그대로 돌리고 뒤집은 뒤 인코딩한다. */
class CrewLogoEncoderTest {
    @Test
    fun `EXIF 방향마다 돌릴 각도와 좌우 뒤집기를 정한다`() {
        val expected = mapOf(
            ExifInterface.ORIENTATION_UNDEFINED to CrewLogoEncoder.Turn(0, false),
            ExifInterface.ORIENTATION_NORMAL to CrewLogoEncoder.Turn(0, false),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to CrewLogoEncoder.Turn(0, true),
            ExifInterface.ORIENTATION_ROTATE_180 to CrewLogoEncoder.Turn(180, false),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to CrewLogoEncoder.Turn(180, true),
            ExifInterface.ORIENTATION_TRANSPOSE to CrewLogoEncoder.Turn(90, true),
            ExifInterface.ORIENTATION_ROTATE_90 to CrewLogoEncoder.Turn(90, false),
            ExifInterface.ORIENTATION_TRANSVERSE to CrewLogoEncoder.Turn(270, true),
            ExifInterface.ORIENTATION_ROTATE_270 to CrewLogoEncoder.Turn(270, false)
        )
        expected.forEach { (orientation, turn) ->
            assertEquals(turn, CrewLogoEncoder.turnOf(orientation), "orientation $orientation")
        }
        assertEquals(CrewLogoEncoder.Turn(0, false), CrewLogoEncoder.turnOf(99), "모르는 값은 그대로")
    }
}
