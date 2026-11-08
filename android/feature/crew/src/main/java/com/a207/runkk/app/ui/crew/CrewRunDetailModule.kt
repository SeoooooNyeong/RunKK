package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.ui.share.RecordResultDetailReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** CrewRunDetailSource ← RecordResultDetailReader.read(크루런 결과 상세 F-16b). */
@Module
@InstallIn(SingletonComponent::class)
internal object CrewRunDetailModule {
    @Provides
    fun crewRunDetails(reader: RecordResultDetailReader): CrewRunDetailSource = CrewRunDetailSource(reader::read)
}
