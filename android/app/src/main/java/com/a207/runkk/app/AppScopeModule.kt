package com.a207.runkk.app

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전체 코루틴 범위(applicationScope)를 그래프에 내준다(6.1.14, D-61).
 * 한정자 없는 CoroutineScope 바인딩은 이것 하나뿐이어야 한다. 포트 ↔ 구현 바인딩은 두지 않는다.
 * 자식 작업(RunTracker의 워치 수집·저장, PushTokenSync 등록)의 처리되지 않은 예외는 프로세스를 죽이지 않고 기록만 한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    private const val TAG = "RunkkAppScope"

    @Provides
    @Singleton
    fun scope(): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, error -> Log.e(TAG, "applicationScope 작업 실패", error) }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }
}
