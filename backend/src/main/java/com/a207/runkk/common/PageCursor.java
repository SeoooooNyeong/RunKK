package com.a207.runkk.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * 커서 페이지 요청(크루 목록·게시글·대결 이력, 단계별 자가 리뷰 B3·B4 "페이지 나누기"). 기존 응답을 깨지 않도록 size가 없으면 전부를 준다. 목록은 최신순(ID
 * 내림차순과 같은 순서)이고 cursor는 앞 페이지 마지막 항목의 ID다.
 *
 * @param cursor 앞 페이지 마지막 항목 ID, 첫 페이지면 null
 * @param size 페이지 크기(1~100), 전부면 null
 */
public record PageCursor(Long cursor, Integer size) {

    /** 페이지 크기 상한. */
    public static final int MAX_SIZE = 100;

    public PageCursor {
        if (size != null && (size < 1 || size > MAX_SIZE)) {
            throw new IllegalArgumentException("페이지 크기는 1~" + MAX_SIZE + ": " + size);
        }
        if (cursor != null && cursor < 1) {
            throw new IllegalArgumentException("커서는 양수: " + cursor);
        }
    }

    /** 전부(페이지 나누기 없음). */
    public static PageCursor all() {
        return new PageCursor(null, null);
    }

    /**
     * 최신순 목록에서 cursor 다음 항목부터 size개. cursor 항목이 목록에 있으면 그 뒤부터, 없으면(지워짐) ID가 cursor보다 작은 항목부터다. 더
     * 남았으면 nextCursor가 이 페이지 마지막 항목 ID다.
     */
    public <T> Slice<T> slice(List<T> newestFirst, ToLongFunction<T> idOf) {
        List<T> rest = newestFirst;
        if (cursor != null) {
            int at = -1;
            for (int i = 0; i < newestFirst.size(); i++) {
                if (idOf.applyAsLong(newestFirst.get(i)) == cursor) {
                    at = i;
                    break;
                }
            }
            if (at >= 0) {
                rest = newestFirst.subList(at + 1, newestFirst.size());
            } else {
                List<T> older = new ArrayList<>();
                for (T item : newestFirst) {
                    if (idOf.applyAsLong(item) < cursor) {
                        older.add(item);
                    }
                }
                rest = older;
            }
        }
        if (size == null || rest.size() <= size) {
            return new Slice<>(List.copyOf(rest), null);
        }
        List<T> page = List.copyOf(rest.subList(0, size));
        return new Slice<>(page, idOf.applyAsLong(page.get(page.size() - 1)));
    }

    /**
     * 한 페이지.
     *
     * @param items 항목
     * @param nextCursor 다음 페이지 커서, 마지막 페이지면 null
     * @param <T> 항목 형식
     */
    public record Slice<T>(List<T> items, Long nextCursor) {

        public Slice {
            items = List.copyOf(items);
        }
    }
}
