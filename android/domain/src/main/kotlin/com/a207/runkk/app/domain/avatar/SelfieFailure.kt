package com.a207.runkk.app.domain.avatar

/**
 * 마지막으로 실패로 끝난 셀카 작업(DV-10). 얼굴 만들기 화면을 나갔다 와도 실패 사실과 사유를 보이고, 서버가 그 작업을 뒤에 성공으로
 * 회수했는지(DV-9) 한 번 다시 묻는 데 쓴다. 사진은 남기지 않는다.
 */
data class SelfieFailure(val jobId: String, val code: String?)
