package com.a207.runkk.app.api.run

import com.a207.runkk.app.api.core.UploadClient
import com.a207.runkk.app.domain.sync.DisposalRemote
import com.a207.runkk.app.domain.sync.RunUploadRemote
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create

/** RunUploadRemote ← RunUploadRemoteImpl, DisposalRemote ← DisposalRemoteImpl(6.1.14, D-61). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RunBindings {
    @Binds
    abstract fun runUploadRemote(impl: RunUploadRemoteImpl): RunUploadRemote

    @Binds
    abstract fun disposalRemote(impl: DisposalRemoteImpl): DisposalRemote

    companion object {
        @Provides
        fun runApi(retrofit: Retrofit): RunApi = retrofit.create()

        /** 기록 전송만 업로드 전용 클라이언트(본문 gzip, Jira 327)로 보낸다. */
        @Provides
        fun runUploadApi(@UploadClient retrofit: Retrofit): RunUploadApi = retrofit.create()
    }
}
