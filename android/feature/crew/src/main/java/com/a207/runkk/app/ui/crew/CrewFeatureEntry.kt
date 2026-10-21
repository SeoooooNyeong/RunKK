package com.a207.runkk.app.ui.crew

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 크루 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14): 크루 탭(찾기), 만들기, 내 크루 홈, 크루 관리,
 * 크루런 모집·대기실/진행·결과.
 */
class CrewFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.CREW

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.CREW) { CrewFindRoute() }
        builder.route(Routes.CREW_CREATE) { CrewCreateRoute() }
        builder.addEntryProvider(Routes.CrewHome::class) { key -> CrewHomeRoute(key) }
        builder.addEntryProvider(Routes.CrewManage::class) { key -> CrewManageRoute(key) }
        builder.addEntryProvider(Routes.CrewRunWrite::class) { key -> CrewRunWriteRoute(key) }
        builder.addEntryProvider(Routes.CrewRunLobby::class) { key -> CrewRunRoute(key) }
        builder.addEntryProvider(Routes.CrewRunResult::class) { key -> CrewRunResultRoute(key.postId) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface CrewEntryModule {
    @Binds
    @IntoSet
    fun bindCrewEntry(entry: CrewFeatureEntry): FeatureEntry
}
