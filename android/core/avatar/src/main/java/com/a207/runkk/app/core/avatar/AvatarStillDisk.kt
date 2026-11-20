package com.a207.runkk.app.core.avatar

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 전신 정지 그림의 기기 사본(공용 칸, S15P31A207-345 9회차). [RiveAvatarStill]이 Rive로 읽은 그림을 남기고, 시작 로딩이 랭킹 시상대
 * 모습의 그림을 읽어 프로세스 캐시([AvatarStillCache])에 올린다([preloadRiveAvatarStills]). 같은 기기·같은 칸 크기면 랭킹 탭 첫 판부터
 * 그림이 바로 보인다(Rive 읽기 없음). 머리를 기기 사본으로 가를 수 없는 모습은 남기지 않는다.
 */
internal object AvatarStillDisk {
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 읽은 그림을 남긴다(뒤에서, 칸이 떠나도 끝까지). */
    fun save(context: Context, look: RiveAvatarLook, width: Int, height: Int, still: Bitmap) {
        val app = context.applicationContext
        writes.launch {
            val disk = AvatarLookDisk.shared(app)
            val key = disk.stillKey(look) ?: return@launch
            disk.writeStill(key, width, height, still)
        }
    }

    /** [disk]에 남긴 [look]의 그림을 칸 크기별로 캐시에 올린다. 올린 수. 막는 읽기다(IO). */
    fun load(disk: AvatarLookDisk, look: RiveAvatarLook): Int {
        val key = disk.stillKey(look) ?: return 0
        val stills = disk.readStills(key)
        stills.forEach { (width, height, still) -> AvatarStillCache.put(AvatarStillKey.of(look, width, height), still) }
        return stills.size
    }
}
