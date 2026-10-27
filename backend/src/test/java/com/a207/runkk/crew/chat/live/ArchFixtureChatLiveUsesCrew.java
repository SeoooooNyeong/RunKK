package com.a207.runkk.crew.chat.live;

import com.a207.runkk.crew.CrewRelation;

/** ArchUnit 음성 시험 전용(규칙 7 위반, TC-6209 ③). crew.chat.live는 crew.chat·common만 import한다. */
public record ArchFixtureChatLiveUsesCrew(long crewId, CrewRelation relation) {}
