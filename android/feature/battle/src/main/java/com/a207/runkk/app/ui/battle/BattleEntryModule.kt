package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface BattleEntryModule {
    @Binds
    @IntoSet
    fun bindBattleEntry(entry: BattleFeatureEntry): FeatureEntry

    /** 친구 대결 초대 공유(D-270). */
    @Binds
    fun bindInviteSharer(sharer: KakaoInviteSharer): InviteSharer
}
