package com.a207.runkk.avatar;

import java.util.EnumMap;
import java.util.Map;

/**
 * 이전 5파츠 얼굴을 읽고 보존하기 위한 PNG 묶음(설계 6.1.8). 원본 셀카가 아니라 변환 결과다.
 *
 * @param images 부위별 PNG 바이트, 5개 모두
 */
public record SdFacePartImages(Map<FacePart, byte[]> images) {

    public SdFacePartImages {
        if (images == null || images.size() != FacePart.values().length) {
            throw new IllegalArgumentException("SD 얼굴 부위 이미지 5개가 모두 있어야 한다");
        }
        images = Map.copyOf(new EnumMap<>(images));
    }
}
