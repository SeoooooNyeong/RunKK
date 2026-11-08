package com.a207.runkk.app.domain.runbody

/**
 * 러닝 선택(G-30) 한 줄의 분석 상태와 러닝 점수(서버 RunBodySessionRow, 계약 v1.2 7절). [score]는 ai 결과 다섯 축 중 점수 있는 축의
 * 평균(반올림, 0~100)이고 DONE일 때만 있다. [change]는 직전 점수 있는 러닝 대비 변화(양수면 올랐다)이고 셈할 수 없으면 null이다.
 */
data class RunBodyRunScore(val recordId: String, val status: RunBodyStatus, val score: Int?, val change: Int?)
