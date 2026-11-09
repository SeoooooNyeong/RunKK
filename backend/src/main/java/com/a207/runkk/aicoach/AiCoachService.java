package com.a207.runkk.aicoach;

import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.ConsentWithdrawn;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.OperationPolicy;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiCoachService {
    private static final Logger log = LoggerFactory.getLogger(AiCoachService.class);
    private static final int RETENTION_DAYS = 90;

    private final CoachChatGateway gateway;
    private final CoachStore store;
    private final MemberQueryService members;
    private final OperationPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final ConcurrentHashMap<Long, Session> active = new ConcurrentHashMap<>();

    public AiCoachService(
            CoachChatGateway gateway,
            CoachStore store,
            MemberQueryService members,
            OperationPolicy policy,
            Clock clock,
            TransactionTemplate transactions) {
        this.gateway = gateway;
        this.store = store;
        this.members = members;
        this.policy = policy;
        this.clock = clock;
        this.transactions = transactions;
    }

    public Session prepare(long memberId, CoachQuestion question) {
        checkAccess(memberId);
        String previous = question.conversationId();
        if (previous != null && !store.hasConversation(memberId, previous, cutoff())) {
            throw new CoachRejected(CoachRejected.Reason.CONVERSATION_EXPIRED);
        }
        int limit = policy.coachDailyLimit();
        CoachStore.Lease lease =
                transactions.execute(
                        status -> {
                            if (!members.lockCurrentCoachConsent(memberId)) {
                                throw new CoachRejected(CoachRejected.Reason.CONSENT_REQUIRED);
                            }
                            return store.claim(memberId, limit, now());
                        });
        Session session = new Session(lease, question);
        if (active.putIfAbsent(memberId, session) != null) {
            release(lease);
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        }
        try {
            session.connect();
            return session;
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    public List<CoachStore.Conversation> conversationsOf(long memberId) {
        checkAccess(memberId);
        return store.conversations(memberId, cutoff());
    }

    public int purgeExpired() {
        return store.purge(cutoff());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onWithdrawal(MemberWithdrawn event) {
        store.deleteMember(event.memberId());
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener
    public void cancelWithdrawnMember(MemberWithdrawn event) {
        cancel(event.memberId());
    }

    @TransactionalEventListener
    public void cancelWithdrawnConsent(ConsentWithdrawn event) {
        if (event.item() == ConsentItem.LLM_TRANSFER) {
            cancel(event.memberId());
        }
    }

    private void cancel(long memberId) {
        Session session = active.get(memberId);
        if (session != null) {
            session.close();
        }
    }

    private void checkAccess(long memberId) {
        if (!policy.coachEnabled()) {
            throw new CoachRejected(CoachRejected.Reason.FEATURE_CLOSED);
        }
        if (!members.hasCurrentCoachConsent(memberId)) {
            throw new CoachRejected(CoachRejected.Reason.CONSENT_REQUIRED);
        }
    }

    private LocalDateTime cutoff() {
        return now().minusDays(RETENTION_DAYS);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void release(CoachStore.Lease lease) {
        try {
            store.release(lease);
        } catch (RuntimeException failure) {
            // 정리 실패가 원래 오류를 가리지 않는다. 임대는 시간이 지나면 해제된다.
            log.warn("coach_lease_release_failed type={}", failure.getClass().getSimpleName());
        }
    }

    public final class Session implements AutoCloseable {
        private final CoachStore.Lease lease;
        private final CoachQuestion question;
        private final AtomicBoolean closed = new AtomicBoolean();
        private CoachChatGateway.Stream stream;

        private Session(CoachStore.Lease lease, CoachQuestion question) {
            this.lease = lease;
            this.question = question;
        }

        // 탈퇴 후 파기 호출은 연결 준비가 끝나고 AI 응답 본문을 닫은 뒤에 실행된다.
        private synchronized void connect() {
            if (closed.get()) {
                throw new CoachRejected(CoachRejected.Reason.RETRY);
            }
            checkAccess(lease.memberId());
            stream = gateway.open(lease.memberId(), question);
        }

        public void relay(CoachChatGateway.Listener output) throws IOException {
            checkOpen();
            stream.read(
                    new CoachChatGateway.Listener() {
                        @Override
                        public void started(String id) throws IOException {
                            checkOpen();
                            boolean stored =
                                    Boolean.TRUE.equals(
                                            transactions.execute(
                                                    status ->
                                                            members.lockCurrentCoachConsent(
                                                                            lease.memberId())
                                                                    && !closed.get()
                                                                    && store.started(
                                                                            lease,
                                                                            id,
                                                                            question.preview(),
                                                                            question
                                                                                            .conversationId()
                                                                                    != null,
                                                                            now())));
                            if (!stored) {
                                throw new IOException("coach conversation index unavailable");
                            }
                            checkOpen();
                            output.started(id);
                        }

                        @Override
                        public void delta(String text) throws IOException {
                            checkOpen();
                            output.delta(text);
                        }

                        @Override
                        public void done() throws IOException {
                            checkOpen();
                            output.done();
                        }
                    });
        }

        public void acknowledgeAnswer() throws IOException {
            checkOpen();
            boolean recorded =
                    Boolean.TRUE.equals(
                            transactions.execute(
                                    status ->
                                            members.lockCurrentCoachConsent(lease.memberId())
                                                    && !closed.get()
                                                    && store.answered(lease, now())));
            if (!recorded) {
                throw new IOException("coach answer can no longer be recorded");
            }
        }

        private void checkOpen() throws IOException {
            if (closed.get()
                    || Thread.currentThread().isInterrupted()
                    || !members.hasCurrentCoachConsent(lease.memberId())) {
                throw new IOException("coach stream closed");
            }
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException failure) {
                log.debug("coach_stream_close_failed type={}", failure.getClass().getSimpleName());
            } finally {
                release(lease);
                active.remove(lease.memberId(), this);
            }
        }
    }
}
