package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome

/**
 * 시작 에셋 미리 받기의 출처 포트(S15P31A207-345). 종류마다 구현이 하나씩 있고(내 머리·내 아바타 합성·랭킹·셀럽런·기록·크루·친구·홈,
 * Hilt 집합으로 모은다) 화면이 쓰는 캐시(내 얼굴 저장소, 화면 사본 저장소, 얼굴 디스크 캐시 등)에 그대로 받아 두어 화면이 먼저 보여 주고
 * 뒤에서만 새로 받게 한다. 로그인 여부를 매개변수로 받지 않고 연산을 나눈다(제어 결합 방지, DeviceCopySync와 같다). 에셋 키는 출처끼리
 * 겹치지 않게 종류 접두어를 붙인다.
 */
interface AssetPreloadSource {
    /** 로그인과 상관없는 공용 에셋 목록. */
    suspend fun publicAssets(): Outcome<List<PreloadAsset>>

    /** 로그인한 회원의 에셋 목록. 목록을 알려고 서버에 물을 수 있다. */
    suspend fun memberAssets(): Outcome<List<PreloadAsset>>

    /** 유효한 캐시(지금 로그인한 계정 칸 또는 공용 칸)에 있는가. 네트워크를 쓰지 않는다. */
    fun isCached(asset: PreloadAsset): Boolean

    /**
     * [asset]을 받아 캐시에 둔다. 파일 하나를 얻을 때마다 [onFile]을 부른다(여러 스레드에서 올 수 있다). 앞 시도에서 얻은 파일은
     * 다시 받지 않고 그것을 쓴다(이어 받기).
     */
    suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit>
}
