package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.Outcome

/**
 * 홈 큰 아바타 미리 준비 포트(S15P31A207-345 2차, 구현 ui.avatar). 시작 로딩 화면에서 홈 아바타가 쓰는 입력(아바타·내 러너·상점, 내
 * 머리)을 모으고 서기 모습 합성 텍스처와 Rive 첫 판(파일 가져오기)을 프로세스 캐시에 미리 만들어, 홈이 뜰 때 아바타 칸이 로딩 원 없이
 * 바로 그리게 한다. 홈 칸과 같은 저장소·같은 캐시 열쇠를 쓴다.
 */
interface MyAvatarPreparation {
    /** 이 프로세스에서 이미 준비를 마쳤는가(네트워크 없음). */
    fun isPrepared(): Boolean

    /** 준비한다. [onStep]은 단계(입력 모으기, 합성·첫 판) 하나를 마칠 때마다 부른다. 입력을 읽지 못하면 그 오류다. */
    suspend fun prepare(onStep: () -> Unit): Outcome<Unit>
}
