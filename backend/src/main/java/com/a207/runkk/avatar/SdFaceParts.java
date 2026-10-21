package com.a207.runkk.avatar;

import java.util.EnumMap;
import java.util.Map;

/**
 * 변환된 SD 얼굴 부위 이미지의 저장 키 묶음(설계 6.1.8). 키는 api의 FacePartImageStore가 만든다(ai 서버는 저장하지 않는다, D-59). 한 번
 * 변환한 부위 5개는 같은 묶음 위치("묶음/부위") 아래에 있다.
 *
 * @param parts 부위별 저장 키, 5개 모두
 */
public record SdFaceParts(Map<FacePart, String> parts) {

    public SdFaceParts {
        if (parts == null || parts.size() != FacePart.values().length) {
            throw new IllegalArgumentException("SD 얼굴 부위 5개가 모두 있어야 한다");
        }
        parts = Map.copyOf(new EnumMap<>(parts));
    }

    /** 부위 키의 공통 위치(마지막 "/" 앞). 앱이 부위 이미지 묶음을 고르는 키다. */
    public String setKey() {
        String eyes = parts.get(FacePart.EYES);
        int slash = eyes.lastIndexOf('/');
        return slash < 0 ? eyes : eyes.substring(0, slash);
    }
}
