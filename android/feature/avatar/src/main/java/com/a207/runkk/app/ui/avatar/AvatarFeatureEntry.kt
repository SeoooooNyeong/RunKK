package com.a207.runkk.app.ui.avatar

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

/** 아바타 만들기·꾸미기·상점 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class AvatarFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.AVATAR

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.AVATAR) { AvatarRoute() }
        builder.route(Routes.AVATAR_SETUP) { AvatarSetupRoute() }
        builder.route(Routes.SHOP) { ShopRoute() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AvatarEntryModule {
    @Binds
    @IntoSet
    fun bindAvatarEntry(entry: AvatarFeatureEntry): FeatureEntry
}
