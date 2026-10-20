package com.a207.runkk.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전체 코루틴 범위(applicationScope)를 그래프에 내준다(6.1.14, D-61).
 * 한정자 없는 CoroutineScope 바인딩은 이것 하나뿐이어야 한다. 포트 ↔ 구현 바인딩은 두지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides
    @Singleton
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
