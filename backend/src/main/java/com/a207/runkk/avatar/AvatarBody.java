package com.a207.runkk.avatar;

import java.util.Map;

/** Node 몸 프리셋과 pack.mobile 합성 정보를 유지한다. */
public record AvatarBody(Map<String, Object> pack) {
    @Override
    public String toString() {
        return "AvatarBody[pack=***]";
    }
}
