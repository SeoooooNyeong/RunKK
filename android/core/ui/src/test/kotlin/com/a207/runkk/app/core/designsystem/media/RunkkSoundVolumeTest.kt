package com.a207.runkk.app.core.designsystem.media

import kotlin.test.assertEquals
import org.junit.Test

/** 소리 크기와 스피커 아이콘 끄기·켜기(FR-237, FR-238, D-350). 기기 저장 없이(저장소 null) 규칙만 본다. */
class RunkkSoundVolumeTest {
    @Test
    fun `처음 크기는 최대이고 끌면 0, 다시 켜면 끄기 전 크기로 돌아간다`() {
        val volume = RunkkSoundVolume(null)
        assertEquals(1f, volume.music)
        volume.changeMusic(0.4f)
        volume.toggleMusic()
        assertEquals(0f, volume.music)
        volume.toggleMusic()
        assertEquals(0.4f, volume.music)
    }

    @Test
    fun `막대로 0까지 내린 뒤 켜면 끄기 전 기억이 없으니 최대로 켠다`() {
        val volume = RunkkSoundVolume(null)
        volume.changeEffects(0f)
        volume.toggleEffects()
        assertEquals(1f, volume.effects)
    }

    @Test
    fun `크기는 0에서 1 사이로 자른다`() {
        val volume = RunkkSoundVolume(null)
        volume.changeMusic(1.5f)
        assertEquals(1f, volume.music)
        volume.changeEffects(-0.2f)
        assertEquals(0f, volume.effects)
    }

    @Test
    fun `배경 음악과 효과음은 따로 끄고 켠다`() {
        val volume = RunkkSoundVolume(null)
        volume.toggleMusic()
        assertEquals(0f, volume.music)
        assertEquals(1f, volume.effects)
    }
}
