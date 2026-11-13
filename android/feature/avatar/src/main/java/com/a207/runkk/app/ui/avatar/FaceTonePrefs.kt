package com.a207.runkk.app.ui.avatar

import android.content.Context
import androidx.core.content.edit
import com.a207.runkk.app.core.designsystem.SelfFaceTone

/**
 * 내 얼굴 그을림([SelfFaceTone])을 기기에 남긴다([임시:D-성-222]). 앱을 다시 켜면 서버 응답을 기다리지 않고 첫 화면부터 얼굴 원이 같은
 * 톤이다. 얼굴 키가 다르면(다른 회원·새 셀카) 얼굴 원이 쓰지 않으므로 앞 값이 남아도 다른 얼굴을 그을리지 않는다.
 */
internal object FaceTonePrefs {
    private const val FILE = "runkk_self_face_tone"
    private const val KEY_FACE = "face_key"
    private const val KEY_AMOUNT = "running_amount"

    fun read(context: Context): SelfFaceTone {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return SelfFaceTone(prefs.getString(KEY_FACE, null), prefs.getInt(KEY_AMOUNT, 0))
    }

    fun write(context: Context, tone: SelfFaceTone) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString(KEY_FACE, tone.faceKey)
            putInt(KEY_AMOUNT, tone.runningAmount)
        }
    }
}
