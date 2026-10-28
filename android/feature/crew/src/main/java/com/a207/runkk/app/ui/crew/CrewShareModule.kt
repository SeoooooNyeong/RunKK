package com.a207.runkk.app.ui.crew

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** CelebrationSharer ← CardCelebrationSharer(축하 카드 공유, FR-141). */
@Module
@InstallIn(SingletonComponent::class)
internal interface CrewShareModule {
    @Binds
    fun bindCelebrationSharer(impl: CardCelebrationSharer): CelebrationSharer
}
