package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 크루런 결과 상세(F-16b)가 쓰는 내 기록 상세(GET /runs/{id}). 기록 저장소의 연산 하나만 쓰므로 그 하나로 좁혀 받는다(대결 결과
 * RunDetailSource와 같은 방식).
 */
fun interface CrewRunDetailSource {
    suspend fun detail(recordId: String): Outcome<RecordDetail>
}

@Module
@InstallIn(SingletonComponent::class)
internal object CrewRunDetailModule {
    @Provides
    fun crewRunDetails(records: RecordRepository): CrewRunDetailSource = CrewRunDetailSource(records::run)
}
