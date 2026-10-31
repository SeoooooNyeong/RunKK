package com.a207.runkk.app.api.battle

import kotlinx.serialization.Serializable

/** 셀프·셀럽 상대 기록 미리 받기 응답(GET /battles/record-tracks, 서버 BattleDtos.RecordTracksResponse, D-377, contract-v2 2절). */
@Serializable
internal data class RecordTracksResponse(val tracks: List<RecordTrackResponse> = emptyList())
