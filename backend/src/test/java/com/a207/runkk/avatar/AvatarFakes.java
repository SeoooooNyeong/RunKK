package com.a207.runkk.avatar;

import com.a207.runkk.NoTransactions;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
        private int sets;

        @Override
        public synchronized SdFaceParts save(long memberId, SdFacePartImages images) {
            String set = "face/" + memberId + "/s" + (++sets);
            Map<FacePart, String> keys = new EnumMap<>(FacePart.class);
            images.images().keySet().forEach(part -> keys.put(part, set + "/" + part.name()));
            SdFaceParts parts = new SdFaceParts(keys);
            saved.put(memberId, parts);
            return parts;
        }

        @Override
        public synchronized void deleteByMember(long memberId) {
            saved.remove(memberId);
        }
    }

    public static final class Locks implements SelfieLockStore {
        final Set<Long> held = new HashSet<>();

        @Override
        public synchronized boolean tryLock(long memberId, Duration ttl) {
            return held.add(memberId);
        }

        @Override
        public synchronized void unlock(long memberId) {
            held.remove(memberId);
        }
    }

    /** 응답을 정할 수 있는 가짜 얼굴 변환. 호출 수를 센다. */
    public static final class Gateway implements FaceTransformGateway {
        int calls;
        Function<byte[], FaceTransformOutcome> answer =
                image -> FaceTransformOutcome.success(images());

        @Override
        public synchronized FaceTransformOutcome transform(byte[] image) {
            calls++;
            return answer.apply(image);
        }
    }
}
