package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ProfileEdit
import javax.inject.Inject

/** PATCH /me/profile에 두 공개 값만 싣는다(닉네임·소개는 null이라 서버가 그대로 둔다). */
internal class AccountProfileVisibilitySaver @Inject constructor(private val accounts: AccountRepository) :
    ProfileVisibilitySaver {
    override suspend fun save(isAgePublic: Boolean, isGenderPublic: Boolean): Outcome<Unit> = accounts.editProfile(
        ProfileEdit(nickname = null, intro = null, isAgePublic = isAgePublic, isGenderPublic = isGenderPublic)
    )
}
