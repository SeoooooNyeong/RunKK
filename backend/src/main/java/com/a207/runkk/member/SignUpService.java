package com.a207.runkk.member;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 가입 유스케이스를 조정한다(설계 6.1.2, SQ-17, srs.md 6.3 1.2). 가입 티켓 검증 → form.validate → 닉네임 확정 →
 * Member.signUp → 저장 → 감사(가입동의) → 토큰 발급.
 */
@Service
public class SignUpService {

    /**
     * 닉네임을 넣지 않았을 때의 이름. 설계는 "제공자제공이름"을 쓰라고 했으나 SocialLoginGateway·가입 티켓이 계정 ID만 싣는다. 앱이 제공자 SDK에서
     * 받은 이름을 닉네임 칸에 미리 채워 보내고, 비어 오면 이 이름에 숫자 4자리를 붙인다(B2 결정).
     */
    static final String DEFAULT_NICKNAME = "러너";

    private static final int MAX_SUFFIX_ATTEMPTS = 5;
    private static final int SUFFIX_BOUND = 10_000;

    private final MemberRepository memberRepository;
    private final List<ReservedNicknameChecker> reservedNicknameCheckers;
    private final TokenIssuer tokenIssuer;
    private final AuditService auditService;
    private final Clock clock;

    public SignUpService(
            MemberRepository memberRepository,
            List<ReservedNicknameChecker> reservedNicknameCheckers,
            TokenIssuer tokenIssuer,
            AuditService auditService,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.reservedNicknameCheckers = List.copyOf(reservedNicknameCheckers);
        this.tokenIssuer = tokenIssuer;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SignUpResult signUp(SignUpCommand command) {
        SocialAccount account;
        try {
            account = tokenIssuer.redeemSignUpTicket(command.signUpTicket());
        } catch (AuthFailureException e) {
            throw new BusinessRuleException(ReasonCode.SIGN_UP_TICKET_INVALID);
        }
        if (memberRepository.findByAccount(account).isPresent()) {
            throw new BusinessRuleException(ReasonCode.ALREADY_MEMBER);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        SignUpForm form = command.form();
        form.validate(now.getYear());
        Nickname nickname = settleNickname(form.nickname());
        Member member = memberRepository.save(Member.signUp(account, form, nickname, now));
        String memberId = String.valueOf(member.getId());
        auditService.record(
                new AuditEntry(
                        memberId,
                        ActorRole.RUNNER,
                        AuditAction.SIGN_UP_CONSENT,
                        memberId,
                        null,
                        describe(form.consents())));
        return new SignUpResult(
                member.getId(), nickname.value(), tokenIssuer.issue(member.getId()));
    }

    /** 닉네임이 겹치면 숫자 4자리를 붙여 5회까지 다시 시도한다(Q-75). 그래도 겹치면 NICKNAME_INVALID. */
    private Nickname settleNickname(String requested) {
        Nickname base = Nickname.of(requested == null ? DEFAULT_NICKNAME : requested);
        if (requested != null && !isTaken(base)) {
            return base;
        }
        for (int i = 0; i < MAX_SUFFIX_ATTEMPTS; i++) {
            Nickname candidate = base.withSuffix(ThreadLocalRandom.current().nextInt(SUFFIX_BOUND));
            if (!isTaken(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException(ReasonCode.NICKNAME_INVALID);
    }

    private boolean isTaken(Nickname nickname) {
        return memberRepository.existsByNickname(nickname.value())
                || reservedNicknameCheckers.stream().anyMatch(c -> c.isReserved(nickname.value()));
    }

    private static String describe(Map<ConsentItem, Boolean> consents) {
        return consents.entrySet().stream()
                .filter(e -> e.getKey().collectedAtSignUp())
                .map(e -> e.getKey() + "=" + Boolean.TRUE.equals(e.getValue()))
                .sorted()
                .collect(Collectors.joining(","));
    }
}
