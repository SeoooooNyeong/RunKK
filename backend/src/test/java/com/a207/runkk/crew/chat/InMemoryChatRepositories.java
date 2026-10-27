package com.a207.runkk.crew.chat;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** 시험용 인메모리 CrewChatMessageRepository·ChatReactionRepository(설계 13.1). */
public final class InMemoryChatRepositories {

    private InMemoryChatRepositories() {}

    /** 인메모리 메시지 저장소. ID는 1부터 차례로 준다. */
    public static class Messages implements CrewChatMessageRepository {
        final Map<Long, CrewChatMessage> rows = new LinkedHashMap<>();
        private long sequence;
        int saves;

        @Override
        public CrewChatMessage save(CrewChatMessage message) {
            saves++;
            if (message.id() == null) {
                setId(message, ++sequence);
            }
            rows.put(message.id(), message);
            return message;
        }

        @Override
        public Optional<CrewChatMessage> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        /** 메모리 저장소는 한 스레드에서만 쓰므로 잠금 없이 같은 행을 준다. */
        @Override
        public Optional<CrewChatMessage> findByIdForUpdate(long id) {
            return findById(id);
        }

        @Override
        public Optional<CrewChatMessage> findByClientMessageId(
                long authorId, String clientMessageId) {
            return rows.values().stream()
                    .filter(m -> Objects.equals(m.authorId(), authorId))
                    .filter(m -> m.clientMessageId().equals(clientMessageId))
                    .findFirst();
        }

        @Override
        public List<CrewChatMessage> findPage(long crewId, ChatCursor cursor) {
            Stream<CrewChatMessage> visible =
                    rows.values().stream().filter(m -> m.crewId() == crewId && m.isVisible());
            if (cursor.after() != null) {
                return visible.filter(m -> m.id() > cursor.after())
                        .sorted(Comparator.comparing(CrewChatMessage::id))
                        .limit(cursor.size() + 1L)
                        .toList();
            }
            if (cursor.before() != null) {
                visible = visible.filter(m -> m.id() < cursor.before());
            }
            return visible.sorted(Comparator.comparing(CrewChatMessage::id).reversed())
                    .limit(cursor.size() + 1L)
                    .toList();
        }

        @Override
        public void anonymizeAuthor(long memberId) {
            rows.values().stream()
                    .filter(m -> Objects.equals(m.authorId(), memberId))
                    .forEach(CrewChatMessage::anonymizeAuthor);
        }

        private static void setId(CrewChatMessage message, long id) {
            try {
                Field field = CrewChatMessage.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(message, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** 인메모리 리액션 저장소. (메시지, 회원, 종류) 하나가 한 행이다. */
    public static class Reactions implements ChatReactionRepository {
        record Key(long messageId, long memberId, ReactionKind kind) {}

        final Map<Key, ChatReaction> rows = new HashMap<>();

        @Override
        public boolean add(ChatReaction r) {
            return rows.putIfAbsent(new Key(r.messageId(), r.memberId(), r.kind()), r) == null;
        }

        @Override
        public boolean remove(long messageId, long memberId, ReactionKind kind) {
            return rows.remove(new Key(messageId, memberId, kind)) != null;
        }

        @Override
        public Map<Long, Map<ReactionKind, Integer>> countsOf(Collection<Long> messageIds) {
            Map<Long, Map<ReactionKind, Integer>> counts = new HashMap<>();
            rows.keySet().stream()
                    .filter(k -> messageIds.contains(k.messageId()))
                    .forEach(
                            k ->
                                    counts.computeIfAbsent(
                                                    k.messageId(),
                                                    id -> new EnumMap<>(ReactionKind.class))
                                            .merge(k.kind(), 1, Integer::sum));
            return counts;
        }

        @Override
        public Map<Long, Set<ReactionKind>> mineOf(long memberId, Collection<Long> messageIds) {
            Map<Long, Set<ReactionKind>> mine = new HashMap<>();
            rows.keySet().stream()
                    .filter(k -> k.memberId() == memberId && messageIds.contains(k.messageId()))
                    .forEach(
                            k ->
                                    mine.computeIfAbsent(
                                                    k.messageId(),
                                                    id -> EnumSet.noneOf(ReactionKind.class))
                                            .add(k.kind()));
            return mine;
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.keySet().removeIf(k -> k.memberId() == memberId);
        }
    }
}
