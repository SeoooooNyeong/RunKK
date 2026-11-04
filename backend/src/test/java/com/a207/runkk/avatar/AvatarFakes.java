package com.a207.runkk.avatar;

import com.a207.runkk.NoTransactions;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** avatar 시험용 인메모리 저장소·가짜 게이트웨이(14장 단위). */
public final class AvatarFakes {

    private AvatarFakes() {}

    static AvatarTransactions transactions() {
        return new AvatarTransactions(NoTransactions.manager());
    }

    /** 부위 5개가 모두 있는 PNG 묶음. */
    public static SdFacePartImages images() {
        Map<FacePart, byte[]> images = new EnumMap<>(FacePart.class);
        for (FacePart part : FacePart.values()) {
            images.put(part, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        }
        return new SdFacePartImages(images);
    }

    public static final class Avatars implements AvatarRepository {
        private final Map<Long, Avatar> avatars = new HashMap<>();

        @Override
        public synchronized Avatar findOrCreate(long memberId) {
            return avatars.getOrDefault(memberId, Avatar.create(memberId));
        }

        @Override
        public synchronized Optional<Avatar> findForUpdate(long memberId) {
            return Optional.of(avatars.computeIfAbsent(memberId, Avatar::create));
        }

        @Override
        public synchronized void save(Avatar avatar) {
            avatars.put(avatar.memberId(), avatar);
        }

        @Override
        public synchronized Stream<Avatar> streamAll() {
            return avatars.values().stream().toList().stream();
        }

        @Override
        public synchronized void deleteByMember(long memberId) {
            avatars.remove(memberId);
        }
    }

    public static final class Images implements FacePartImageStore {
        final Map<Long, SdFaceParts> saved = new HashMap<>();
        final Map<String, byte[]> bytes = new HashMap<>();
        private int sets;

        @Override
        public synchronized Optional<byte[]> find(String imageKey) {
            return Optional.ofNullable(bytes.get(imageKey));
        }

        @Override
        public synchronized SdFaceParts save(long memberId, SdFacePartImages images) {
            String set = "face/" + memberId + "/s" + (++sets);
            Map<FacePart, String> keys = new EnumMap<>(FacePart.class);
            images.images()
                    .forEach(
                            (part, png) -> {
                                keys.put(part, set + "/" + part.name());
                                bytes.put(set + "/" + part.name(), png);
                            });
            SdFaceParts parts = new SdFaceParts(keys);
            saved.put(memberId, parts);
            return parts;
        }

        @Override
        public synchronized void deleteByMember(long memberId) {
            saved.remove(memberId);
            bytes.keySet().removeIf(k -> k.startsWith("face/" + memberId + "/"));
        }
    }

    public static final class Failures implements SelfieFailureStore {
        final Map<String, Integer> counts = new HashMap<>();

        @Override
        public synchronized int failuresOn(long memberId, java.time.LocalDate day) {
            return counts.getOrDefault(memberId + "@" + day, 0);
        }

        @Override
        public synchronized void recordFailure(long memberId, java.time.LocalDate day) {
            counts.merge(memberId + "@" + day, 1, Integer::sum);
        }
    }
}
