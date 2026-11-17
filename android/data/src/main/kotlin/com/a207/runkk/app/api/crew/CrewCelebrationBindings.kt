package com.a207.runkk.app.api.crew

import com.a207.runkk.app.domain.crew.CrewCelebrationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** CrewCelebrationStore ← SharedPreferences(D-655). 다른 묶음과 CrewBindings를 함께 고치지 않게 따로 둔다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CrewCelebrationBindings {
    @Binds
    abstract fun crewCelebrationStore(impl: PreferencesCrewCelebrationStore): CrewCelebrationStore
}
