package com.a207.runkk.watch.guide

import com.a207.runkk.app.domain.run.GuidanceOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** watch.guide의 포트 구현을 Hilt에 묶는다(3.2, D-385 R64): GuidanceOutput ← WatchGuidanceOutput. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchGuideBindings {
    @Binds
    abstract fun output(o: WatchGuidanceOutput): GuidanceOutput
}
