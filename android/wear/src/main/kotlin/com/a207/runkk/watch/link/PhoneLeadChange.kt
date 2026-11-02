package com.a207.runkk.watch.link

/** 폰이 보낸 추월·역전 한 건. [isOvertook]이 참이면 내가 앞질렀고 거짓이면 상대가 앞질렀다(LeadChangeMessage.kind). */
data class PhoneLeadChange(val isOvertook: Boolean, val receivedAtMillis: Long)
