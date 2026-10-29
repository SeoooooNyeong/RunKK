package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.domain.record.RecordRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object BattleProvideModule {
    /** 대결 결과 2쪽의 본인 기록 상세(D-283). */
    @Provides
    fun runDetails(records: RecordRepository): RunDetailSource = RunDetailSource(records::run)
}
