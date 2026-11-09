package com.a207.runkk.app.core.avatar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 남자 선택 몸 원본(1122×1402)의 피부 판정(PoC MaleSelectedBodyContractTest의 픽셀 값 부분). 원본 그림은 Rive 파일에 내장되어
 * 시험 자료로 꺼내지 않으므로 좌표·색 규칙만 본다. 체형을 키우지 않도록 피부 범위는 원본 몸 안(y 708..1179)에만 있다.
 */
class MaleSelectedBodyTest {
    @Test
    fun `따뜻한 크림 배경과 투명 픽셀은 몸 옆에서도 피부가 아니다`() {
        assertFalse(MaleSelectedBody.isSkin(350, 900, 0xfff5e9df.toInt()))
        assertFalse(MaleSelectedBody.isSkin(565, 800, 0xfffbe7da.toInt()))
        assertFalse(MaleSelectedBody.isSkin(504, 1075, 0x00fbe7da))
    }

    @Test
    fun `피부색이어도 목·팔·다리 다각형 밖이나 몸 범위 밖이면 피부가 아니다`() {
        val peach = 0xffe8b090.toInt()
        assertTrue(MaleSelectedBody.isSkin(565, 720, peach), "목")
        assertTrue(MaleSelectedBody.isSkin(444, 861, peach), "왼팔")
        assertTrue(MaleSelectedBody.isSkin(504, 1075, peach), "왼다리")
        assertFalse(MaleSelectedBody.isSkin(565, 800, peach), "상의 자리")
        assertFalse(MaleSelectedBody.isSkin(565, 600, peach), "머리 자리")
        assertFalse(MaleSelectedBody.isSkin(565, 1290, peach), "신발 자리")
        assertFalse(MaleSelectedBody.isSkin(651, 855, peach), "안쪽 팔 옆 셔츠")
    }
}
