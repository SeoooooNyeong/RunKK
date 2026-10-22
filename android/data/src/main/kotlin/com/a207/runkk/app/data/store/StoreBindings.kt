package com.a207.runkk.app.data.store

import android.content.Context
import androidx.room.Room
import com.a207.runkk.app.domain.run.PendingRunRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** PendingRunRepository ← RoomPendingRunRepository(6.1.14, D-61). Room 데이터베이스는 이 패키지 밖에 내지 않는다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class StoreBindings {
    @Binds
    abstract fun pendingRunRepository(impl: RoomPendingRunRepository): PendingRunRepository

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): PendingRunDatabase =
            Room.databaseBuilder(context, PendingRunDatabase::class.java, PendingRunDatabase.NAME).build()
    }
}
