package com.a207.runkk.avatar;

/**
 * 기본 캐릭터 얼굴(설계 6.1.8).
 *
 * @param character 기본 캐릭터 4종 중 하나
 */
public record DefaultFace(DefaultCharacter character) implements Face {

    static final String PREFIX = "default:";

    /** "default:" + 이름. */
    @Override
    public String renderKey() {
        return PREFIX + character.name();
    }
}
