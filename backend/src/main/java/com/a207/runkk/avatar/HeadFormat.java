package com.a207.runkk.avatar;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 머리 응답 형식 판 하나(v0.14 설계 5.33, 판 하나로 모음). 통짜 머리(GET /heads/{회원}/{작업})의 ETag, 메타의 format·ETag, 층
 * ETag와 층 주소의 {@code f} 쿼리가 모두 이 값에서 나온다. 응답 모양·인코딩·축소 방식 가운데 하나라도 바꾸면 이 값을 올린다: 층 주소가 바뀌어 앱의
 * immutable 사본이 자연히 버려지고, ETag가 바뀌어 통짜·메타 사본도 다시 받는다.
 */
public final class HeadFormat {

    /**
     * 지금 형식 판. v2: 통짜 응답에서 frontImage·rightImage·mobile.renderFaceImage를 뺐다. v3: avatar-ai가 저장된 머리
     * 6레이어를 팔레트 PNG로 다시 줄였다(운영 v0.13.4). 층·메타도 이 판을 쓴다.
     */
    public static final String VERSION = "v3";

    /** 층 주소에서 형식 판을 싣는 쿼리 이름. */
    public static final String QUERY = "f";

    private static final String HEADS = "/api/v1/avatar/heads/";

    private HeadFormat() {}

    /** 요청의 형식 판이 지금 판인가. 없거나 다르면 거짓(옛 주소). */
    public static boolean isCurrent(String format) {
        return VERSION.equals(format);
    }

    /** 층 하나의 상대 주소: /api/v1/avatar/heads/{회원}/{작업}/layers/{층}?size={n}&f={판}. 앱은 이 주소를 그대로 쓴다. */
    public static String layerUrl(HeadLayerKey key) {
        return HEADS
                + key.ownerId()
                + "/"
                + URLEncoder.encode(key.jobId(), StandardCharsets.UTF_8).replace("+", "%20")
                + "/layers/"
                + key.layer().pathName()
                + "?size="
                + key.size()
                + "&"
                + QUERY
                + "="
                + VERSION;
    }
}
