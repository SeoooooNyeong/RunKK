package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 가입·설정 화면이 서버로 보내는 문자열(동의 항목·설정 키·값·고지문서 종류·알림 종류)을 서버 enum 이름 목록과 대조한다. */
class ServerContractTest {
    private fun assertAllIn(enumName: String, actual: Collection<String>, what: String) {
        val unknown = actual.toSet() - ContractEnums.names(enumName)
        assertTrue(unknown.isEmpty(), "$what: 서버 ${enumName}에 없는 값 $unknown")
    }

    @Test
    fun `가입 동의 항목은 서버 ConsentItem이고 필수 표시가 서버와 같다`() {
        assertAllIn("ConsentItem", SignUpConsents.items.map { it.item }, "가입 동의 항목")
        assertEquals(
            ContractEnums.names("ConsentItem.required"),
            SignUpConsents.items.filter { it.required }.map { it.item }.toSet(),
            "필수 동의 항목이 서버 ConsentItem(required)와 다르다"
        )
    }

    @Test
    fun `고지문서 종류는 서버 DocumentType이다`() {
        val types = SignUpConsents.items.mapNotNull { it.legalType } +
            listOf(LegalTypes.TERMS, LegalTypes.PRIVACY_POLICY, LegalTypes.LOCATION_TERMS, LegalTypes.HELP)
        assertAllIn("DocumentType", types, "고지문서")
        assertEquals(ContractEnums.names("DocumentType"), types.toSet() + LegalTypes.HELP)
    }

    @Test
    fun `동의 설정 키는 서버 ConsentItem이다`() {
        assertAllIn(
            "ConsentItem",
            listOf(
                ConsentKeys.LOCATION,
                ConsentKeys.HEART_RATE,
                ConsentKeys.MARKETING,
                ConsentKeys.LLM_TRANSFER,
                ConsentKeys.FACE_TRANSFER
            ),
            "동의 키"
        )
    }

    @Test
    fun `기록 공개 범위 선택지는 서버 RecordVisibility와 같다`() {
        assertEquals(ContractEnums.names("RecordVisibility"), RecordVisibilityOptions.values.map { it.first }.toSet())
        assertEquals("PUBLIC", SettingKeys.DEFAULTS.getValue(SettingKeys.RECORD_VISIBILITY))
    }

    @Test
    fun `설정 항목 키의 앞부분은 서버 SettingItem이다`() {
        val keys = listOf(
            SettingKeys.UNIT,
            SettingKeys.RECORD_VISIBILITY,
            SettingKeys.COURSE_MAP_SHARED,
            SettingKeys.PROFILE_VISIBILITY,
            SettingKeys.WATCH_LINKED,
            SettingKeys.RUN_TARGET,
            SettingKeys.RUN_VOICE,
            SettingKeys.RUN_VIBRATION,
            SettingKeys.RUN_FOLLOW_COACH_PLAN,
            SettingKeys.BATTLE_VOICE,
            SettingKeys.BATTLE_VIBRATION,
            SettingKeys.BATTLE_GAP_ALERT,
            SettingKeys.alert("PLAN_MISSED")
        )
        assertAllIn("SettingItem", keys.map { it.substringBefore('.') }, "설정 키")
        assertAllIn("SettingItem", SettingKeys.DEFAULTS.keys.map { it.substringBefore('.') }, "설정 기본값 키")
    }

    @Test
    fun `설정 값 선택지는 서버 enum과 같다`() {
        assertEquals(ContractEnums.names("DistanceUnit"), SettingChoices.unit.map { it.first }.toSet())
        assertEquals(
            ContractEnums.names("ProfileVisibility"),
            SettingChoices.profileVisibility.map {
                it.first
            }.toSet()
        )
        assertEquals(ContractEnums.names("FreeRunTarget"), FreeRunTargets.values.map { it.first }.toSet())
        assertEquals(ContractEnums.names("GapAlertInterval"), GapAlertIntervals.values.map { it.first }.toSet())
    }

    @Test
    fun `설정 기본값은 서버가 받는 값이다`() {
        val defaults = SettingKeys.DEFAULTS
        assertAllIn("DistanceUnit", listOf(defaults.getValue(SettingKeys.UNIT)), "단위 기본값")
        assertAllIn("ProfileVisibility", listOf(defaults.getValue(SettingKeys.PROFILE_VISIBILITY)), "공개 기본값")
        assertAllIn("FreeRunTarget", listOf(defaults.getValue(SettingKeys.RUN_TARGET)), "자유런 목표 기본값")
        assertAllIn("GapAlertInterval", listOf(defaults.getValue(SettingKeys.BATTLE_GAP_ALERT)), "격차 안내 기본값")
    }

    @Test
    fun `알림 종류 12개는 서버 NotificationType과 같다`() {
        assertEquals(ContractEnums.names("NotificationType"), NotificationTypes.values.map { it.first }.toSet())
        assertEquals(12, NotificationTypes.values.size)
    }

    @Test
    fun `가입 입력의 성별은 서버 Gender다`() {
        assertEquals(
            ContractEnums.names("Gender"),
            com.a207.runkk.app.domain.account.Gender.entries.map {
                it.name
            }.toSet()
        )
    }
}
