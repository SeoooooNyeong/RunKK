package com.a207.runkk.analysis;

import java.util.List;

/**
 * 기록 목록(설계 6.1.4 RecordQueryService.list, FR-058). 최신순. 본인 기기의 미전송 기록은 앱이 합쳐서 보인다.
 *
 * @param ownerId 기록 주인
 * @param runs 기록 줄
 */
public record RecordListView(long ownerId, List<RecordRow> runs) {}
