package com.a207.runkk.runbody;

import java.util.List;

/**
 * 러닝 선택 목록(G-30) 한 쪽. 작업을 만든 순서(러닝 저장 순서)의 역순이다.
 *
 * @param items 이 쪽의 줄
 * @param nextCursor 다음 쪽을 읽을 커서(이 쪽 마지막 기록 ID). 더 없으면 null
 */
public record RunBodySessionPage(List<RunBodySessionItem> items, String nextCursor) {

    public RunBodySessionPage {
        items = List.copyOf(items);
    }
}
