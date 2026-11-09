package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository

/**
 * 얼굴 변환 서비스 셀카 전송 동의(서버 ConsentItem FACE_TRANSFER, 피그마 H-1 셀카 카드의 체크, S15P31A207-248).
 * 얼굴 만들기 화면은 동의를 읽고 주기만 한다. 철회는 마이 > 동의 관리에서 한다.
 */
internal interface FaceConsent {
    /** 동의했는가. 읽지 못하면 null이다. */
    suspend fun isGiven(): Boolean?

    suspend fun give(): Outcome<Unit>

    companion object {
        const val ITEM = "FACE_TRANSFER"

        fun of(accounts: AccountRepository): FaceConsent = object : FaceConsent {
            override suspend fun isGiven(): Boolean? =
                accounts.settings().getOrNull()?.let { it.consents[ITEM] == true }

            override suspend fun give(): Outcome<Unit> = accounts.giveConsent(ITEM)
        }
    }
}
