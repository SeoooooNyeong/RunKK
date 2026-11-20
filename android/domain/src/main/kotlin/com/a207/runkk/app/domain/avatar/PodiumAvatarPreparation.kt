package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.Outcome

/**
 * 랭킹 시상대 아바타 미리 준비 포트(S15P31A207-345 9회차, 구현 ui.avatar). 시작 로딩 화면에서 랭킹 탭 첫 화면(개인·주간) 1~3위 러너의
 * 머리와 시상대가 그리는 전신 정지 그림(기기 공용 칸에 남긴 것, 없으면 그 모습의 합성)을 프로세스 캐시에 올려, 로딩 뒤 랭킹 탭 첫 판부터
 * 시상대 아바타가 보이게 한다. 시상대 칸과 같은 캐시 열쇠(모습·같은 머리 인스턴스·칸 크기)를 쓴다.
 */
interface PodiumAvatarPreparation {
    /** 이 프로세스에서 이미 준비를 마쳤는가(네트워크 없음). */
    fun isPrepared(): Boolean

    /** 준비한다. [onStep]은 시상대 자리 하나(1~3위)를 마칠 때마다 부른다. 랭킹을 받지 못하면 그 오류다. */
    suspend fun prepare(onStep: () -> Unit): Outcome<Unit>

    companion object {
        /** 시상대 자리 수. */
        const val PODIUM_SIZE = 3
    }
}
