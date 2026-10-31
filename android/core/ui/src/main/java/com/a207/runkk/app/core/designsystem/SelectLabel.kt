package com.a207.runkk.app.core.designsystem

/**
 * 고르기 칸([RunkkSelectField]) 선택지 하나의 화면 문구와 보조 기술 읽기 문구(D-364). 읽기 문구를 따로 주지 않으면 화면 문구를
 * 읽는다. 페이스 "5:30"처럼 화면 문구를 그대로 읽으면 뜻이 흐려지는 선택지만 읽기 문구를 준다.
 */
data class SelectLabel(val text: String, val spoken: String = text)
