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

/**
 * 자동 이전이 보관해 둔 미전송 기록을 잃지 않는다. 판 1 → 2(늦은 심박 항목 표 추가, D-77), 판 2 → 3(정지 구간 칼럼 추가, FR-194,
 * TC-6521·TC-6523), 판 3 → 4(시작 기기 칼럼 추가, 기본 PHONE, W23).
 */
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

    @Test
    fun `TC-6521 판 2의 미전송 2건과 늦은 심박 항목이 판 3으로 옮겨도 남고 정지 구간은 비어 있다`() {
        helper.createDatabase(2).use { v2 ->
            v2.execSQL(
                "INSERT INTO pending_run VALUES ('r-1', 'FREE', 'FREE_RUN', NULL, NULL, 0, 1000, 100, " +
                    "x'', x'', x'', 'NOT_SENT', 0, NULL)"
            )
            v2.execSQL(
                "INSERT INTO pending_run VALUES ('r-2', 'BATTLE', 'BATTLE_RUN', 7, NULL, 5, 2000, 200, " +
                    "x'', x'', x'', 'NOT_SENT', 5, NULL)"
            )
            v2.execSQL("INSERT INTO late_heart_rate VALUES ('r-9', x'', 0)")
        }

        helper.runMigrationsAndValidate(3).use { v3 ->
            v3.prepare(
                "SELECT recordId, distanceMeters, length(pauses) FROM pending_run ORDER BY recordId"
            ).use { row ->
                row.step()
                assertEquals("r-1", row.getText(0))
                assertEquals(100L, row.getLong(1))
                assertEquals(0L, row.getLong(2), "정지 구간 빈 BLOB = 빈 목록")
                row.step()
                assertEquals("r-2", row.getText(0))
                assertEquals(200L, row.getLong(1))
            }
            v3.prepare("SELECT COUNT(*) FROM late_heart_rate").use { row ->
                row.step()
                assertEquals(1L, row.getLong(0))
            }
        }
        assertEquals(emptyList(), decodePauses(ByteArray(0)))
    }

    @Test
    fun `TC-6523 판 1의 미전송 기록이 판 3까지 옮겨도 남는다`() {
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO pending_run VALUES ('r-1', 'FREE', 'FREE_RUN', NULL, NULL, 0, 1000, 100, " +
                    "x'', x'', x'', 'NOT_SENT', 0, NULL)"
            )
        }

        helper.runMigrationsAndValidate(3).use { v3 ->
            v3.prepare("SELECT recordId, length(pauses) FROM pending_run").use { row ->
                row.step()
                assertEquals("r-1", row.getText(0))
                assertEquals(0L, row.getLong(1))
            }
            v3.prepare("SELECT COUNT(*) FROM late_heart_rate").use { row ->
                row.step()
                assertEquals(0L, row.getLong(0))
            }
        }
    }

    @Test
    fun `판 3의 미전송 기록이 판 4로 옮겨도 남고 시작 기기는 PHONE이다(W23, D-378)`() {
        helper.createDatabase(3).use { v3 ->
            v3.execSQL(
                "INSERT INTO pending_run VALUES ('r-1', 'FREE', 'FREE_RUN', NULL, NULL, 0, 1000, 100, " +
                    "x'', x'', x'', 'NOT_SENT', 0, NULL, x'')"
            )
        }

        helper.runMigrationsAndValidate(4).use { v4 ->
            v4.prepare("SELECT recordId, startDevice FROM pending_run").use { row ->
                row.step()
                assertEquals("r-1", row.getText(0))
                assertEquals("PHONE", row.getText(1))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
