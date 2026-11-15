package com.a207.runkk.app.api.crew

import android.content.Context
import com.a207.runkk.app.domain.crew.CrewCelebrationStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 축하 팝업을 본 주를 앱 전용 SharedPreferences에 둔다(D-655, PreferencesPendingForfeitStore와 같은 방식). 크루마다 마지막으로
 * 본 주의 월요일 하나만 남긴다(지난 주는 다시 띄울 일이 없다).
 */
@Singleton
internal class PreferencesCrewCelebrationStore @Inject constructor(@ApplicationContext context: Context) :
    CrewCelebrationStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isSeen(crewId: Long, weekStart: LocalDate): Boolean =
        prefs.getString(key(crewId), null) == weekStart.toString()

    override fun markSeen(crewId: Long, weekStart: LocalDate) {
        prefs.edit().putString(key(crewId), weekStart.toString()).apply()
    }

    private fun key(crewId: Long) = "seen_$crewId"

    private companion object {
        const val FILE = "runkk_crew_celebration"
    }
}
