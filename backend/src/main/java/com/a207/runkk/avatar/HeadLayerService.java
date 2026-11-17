package com.a207.runkk.avatar;

import org.springframework.stereotype.Service;

/**
 * 다른 러너(와 나)의 머리를 층별로 내준다(S15P31A207-300 레이어 합성). 매 요청 기존 머리와 같은 공개 규칙({@link
 * SelfieJobService#requireVisibleHead})을 확인한 뒤 {@link HeadLayerCache}에서 꺼낸다(ai 호출은 머리마다 한 번).
 */
@Service
public class HeadLayerService {
    private final SelfieJobService jobs;
    private final HeadLayerCache cache;

    public HeadLayerService(SelfieJobService jobs, HeadLayerCache cache) {
        this.jobs = jobs;
        this.cache = cache;
    }

    public HeadMeta meta(long viewerId, long ownerId, String jobId) {
        jobs.requireVisibleHead(viewerId, ownerId, jobId);
        return cache.meta(ownerId, jobId);
    }

    public byte[] layer(long viewerId, long ownerId, String jobId, HeadLayer layer, int size) {
        requireSize(size);
        jobs.requireVisibleHead(viewerId, ownerId, jobId);
        return cache.layer(ownerId, jobId, layer, size);
    }

    /** 앱이 가진 사본을 재검증(304)할 때: ai를 부르지 않고 공개 규칙만 확인한다. */
    public void requireVisible(long viewerId, long ownerId, String jobId) {
        jobs.requireVisibleHead(viewerId, ownerId, jobId);
    }

    /** 내주는 크기(256·512·1024)가 아니면 IllegalArgumentException(400). */
    public static void requireSize(int size) {
        if (!HeadLayerCache.SIZES.contains(size)) {
            throw new IllegalArgumentException("머리 층 크기 오류");
        }
    }
}
