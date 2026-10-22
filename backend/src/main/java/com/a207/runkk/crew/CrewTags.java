package com.a207.runkk.crew;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 성향 태그 묶음 ↔ 저장 문자열(쉼표 구분 태그 이름, member와 같은 형식). crew 안에서만 쓴다. */
final class CrewTags {

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
        Set<PersonalityTag> tags =
                Arrays.stream(encoded.split(","))
                        .map(PersonalityTag::valueOf)
                        .collect(Collectors.toSet());
        return TagSet.of(tags, owner);
    }
}
