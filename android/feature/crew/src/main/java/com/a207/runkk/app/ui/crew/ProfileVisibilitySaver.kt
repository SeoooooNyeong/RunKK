package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ProfileEdit
import javax.inject.Inject

/**
 * 계정 프로필의 나이대·성별 공개를 저장한다(피그마 F-23 내 정보 시트의 토글, 결정: 계정 공개 설정을 함께 저장). 크루 홈이
 * AccountRepository 전체에 기대지 않게 좁힌 포트다(시험은 람다로 바꾼다).
 */
fun interface ProfileVisibilitySaver {
    suspend fun save(isAgePublic: Boolean, isGenderPublic: Boolean): Outcome<Unit>
}
