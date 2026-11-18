package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.domain.record.RunBodyPreviewSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface RunBodyEntryModule {
    @Binds
    @IntoSet
    fun bindRunBodyEntry(entry: RunBodyFeatureEntry): FeatureEntry

    /** 기록 상세의 런바디 진입 카드 상태(domain.record 포트). */
    @Binds
    fun bindRunBodyPreview(reader: RunBodyPreviewReader): RunBodyPreviewSource
}
