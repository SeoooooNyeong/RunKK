package com.a207.runkk.crew;

import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 크루 태그 묶음 ↔ 저장 문자열(쉼표 구분 CrewTag 이름). crew 안에서만 쓴다. 모르는 이름(옛 성향 태그, V39에서 지움)은 건너뛰어 읽기가 깨지지 않게 한다.
 */
final class CrewTags {

    private static final Set<String> KNOWN =
            Arrays.stream(CrewTag.values()).map(Enum::name).collect(Collectors.toSet());

    private CrewTags() {}

    static String encode(TagSet tags) {
        return tags == null
                ? ""
                : tags.tags().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    static TagSet decode(String encoded, TagOwner owner) {
        if (encoded == null || encoded.isBlank()) {
            return TagSet.empty(owner);
        }
        Set<CrewTag> tags = EnumSet.noneOf(CrewTag.class);
        Arrays.stream(encoded.split(","))
                .filter(KNOWN::contains)
                .map(CrewTag::valueOf)
                .forEach(tags::add);
        return tags.isEmpty() ? TagSet.empty(owner) : TagSet.of(tags, owner);
    }
}
