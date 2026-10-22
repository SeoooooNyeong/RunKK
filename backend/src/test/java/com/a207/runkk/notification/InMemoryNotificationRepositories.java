package com.a207.runkk.notification;

import com.a207.runkk.common.DateRange;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 시험용 인메모리 NotificationRepository·PushDeviceRepository(설계 13.1). */
public final class InMemoryNotificationRepositories {

    private InMemoryNotificationRepositories() {}

    /** 인메모리 알림 저장소. */
    public static class Notifications implements NotificationRepository {
        private final Map<Long, Notification> rows = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Notification save(Notification notification) {
            if (notification.id() == null) {
                setId(notification, ++sequence);
            }
            rows.put(notification.id(), notification);
            return notification;
        }

        @Override
        public List<Notification> findByRecipient(long recipientId, Optional<DateRange> range) {
            return rows.values().stream()
                    .filter(n -> n.recipientId() == recipientId)
                    .filter(
                            n ->
                                    range.map(r -> r.contains(n.occurredAt().toLocalDate()))
                                            .orElse(true))
                    .sorted(Comparator.comparing(Notification::occurredAt).reversed())
                    .toList();
        }

        @Override
        public Optional<Notification> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public int deleteOlderThan(LocalDateTime cutoff) {
            int before = rows.size();
            rows.values().removeIf(n -> n.occurredAt().isBefore(cutoff));
            return before - rows.size();
        }

        @Override
        public void deleteByRecipient(long recipientId) {
            rows.values().removeIf(n -> n.recipientId() == recipientId);
        }

        public int size() {
            return rows.size();
        }

        private static void setId(Notification n, long id) {
            try {
                Field f = Notification.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(n, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** 인메모리 기기 토큰 저장소. */
    public static class Devices implements PushDeviceRepository {
        private final Map<String, PushDevice> rows = new LinkedHashMap<>();

        @Override
        public void save(PushDevice device) {
            rows.put(device.token(), device);
        }

        @Override
        public Optional<PushDevice> findByToken(String token) {
            return Optional.ofNullable(rows.get(token));
        }

        @Override
        public List<String> tokensOf(long memberId) {
            return rows.values().stream()
                    .filter(d -> d.memberId() == memberId)
                    .map(PushDevice::token)
                    .toList();
        }

        @Override
        public void deleteTokens(Collection<String> tokens) {
            tokens.forEach(rows::remove);
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.values().removeIf(d -> d.memberId() == memberId);
        }

        public int size() {
            return rows.size();
        }
    }
}
