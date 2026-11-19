package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome

/**
 * 시작 에셋 미리 받기의 출처 포트(S15P31A207-345, 구현 api.avatar). 화면이 쓰는 캐시(내 얼굴 저장소 등)에 그대로 받아 두어 화면이
 * 다시 받지 않게 한다. 로그인 여부를 매개변수로 받지 않고 연산을 나눈다(제어 결합 방지, DeviceCopySync와 같다).
 */
interface AssetPreloadSource {
    /** 로그인과 상관없는 공용 에셋 목록. */
    suspend fun publicAssets(): Outcome<List<PreloadAsset>>

    /** 로그인한 회원의 에셋 목록(내 아바타). 목록을 알려고 서버에 물을 수 있다. */
    suspend fun memberAssets(): Outcome<List<PreloadAsset>>

    /** 유효한 캐시(형식 판이 맞는 기기 사본)에 있는가. 네트워크를 쓰지 않는다. */
    fun isCached(asset: PreloadAsset): Boolean

    /**
     * [asset]을 받아 캐시에 둔다. 파일 하나를 얻을 때마다 [onFile]을 부른다(여러 스레드에서 올 수 있다). 앞 시도에서 얻은 파일은
     * 다시 받지 않고 그것을 쓴다(이어 받기).
     */
    suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit>

    /** 받기·적중 표식 한 줄(네트워크 로그로 확인, 예: "AssetPreload hit …"). */
    fun mark(line: String)
}
