package com.a207.runkk.avatar;

import java.util.Map;
import java.util.Optional;

/** 얼굴을 복원할 때 필요한 값. 단건 아바타와 얼굴 묶음 투영이 같은 선택 규칙을 쓴다. */
public record AvatarFace(
        long memberId, DefaultCharacter character, String headJobId, Map<FacePart, String> parts) {

    public AvatarFace {
        parts = Map.copyOf(parts);
    }

    public Optional<Face> face() {
        if (character != null) {
            return Optional.of(new DefaultFace(character));
        }
        if (headJobId != null) {
            return Optional.of(() -> "head:" + memberId + ":" + headJobId);
        }
        if (parts.size() == FacePart.values().length) {
            return Optional.of(new SelfieFace(new SdFaceParts(parts)));
        }
        return Optional.empty();
    }
}
