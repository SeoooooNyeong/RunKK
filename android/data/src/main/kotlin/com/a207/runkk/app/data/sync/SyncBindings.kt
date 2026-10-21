package com.a207.runkk.app.data.sync

import com.a207.runkk.app.domain.run.UploadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** UploadScheduler ← WorkManagerUploadScheduler(6.1.14, D-61). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindings {
    @Binds
    abstract fun uploadScheduler(impl: WorkManagerUploadScheduler): UploadScheduler
}
