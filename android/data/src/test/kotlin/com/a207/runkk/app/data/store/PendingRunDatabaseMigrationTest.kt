package com.a207.runkk.app.data.store

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 판 1 → 2(늦은 심박 항목 표 추가, D-77) 자동 이전이 보관해 둔 미전송 기록을 잃지 않는다. */
@RunWith(RobolectricTestRunner::class)
class PendingRunDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath(DB),
            driver = AndroidSQLiteDriver(),
            databaseClass = PendingRunDatabase::class
        )

    @Test
    fun `판 1의 미전송 기록이 판 2로 옮겨도 남고 늦은 심박 표가 생긴다`() {
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO pending_run VALUES ('r-1', 'FREE', 'FREE_RUN', NULL, NULL, 0, 1000, 100, " +
                    "x'', x'', x'', 'NOT_SENT', 0, NULL)"
            )
        }

        helper.runMigrationsAndValidate(2).use { v2 ->
            v2.prepare("SELECT recordId FROM pending_run").use { row ->
                row.step()
                assertEquals("r-1", row.getText(0))
            }
            v2.prepare("SELECT COUNT(*) FROM late_heart_rate").use { row ->
                row.step()
                assertEquals(0L, row.getLong(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
