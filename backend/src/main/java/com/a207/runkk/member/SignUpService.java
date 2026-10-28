package com.a207.runkk.member;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 가입·게스트 시작 유스케이스를 조정한다(설계 6.1.2, SQ-17, SQ-18, srs.md 6.3 1.2). 가입: 가입 티켓 검증 → form.validate →
 * 닉네임 확정 → Member.signUp → 저장 → 감사(가입동의) → 토큰 발급. 게스트 시작: form.validate → 기본 닉네임 → Member.guest →
 * 저장(회원 ID 확정) → GuestCredential.issue → attachGuestCredential(같은 트랜잭션, D-128) → 감사 → 토큰 발급.
 */
@Service
public class SignUpService {

    /** 제공자 이름 닉네임이 겹칠 때 숫자 4자리 붙이기 재시도(Q-75). 그래도 겹치면 NICKNAME_TAKEN. */
    private static final int MAX_SUFFIX_ATTEMPTS = 5;

    private static final int SUFFIX_BOUND = 10_000;

    /** 기본 닉네임 4자리 후보 수(D-90). */
    private static final int FOUR_DIGIT_ATTEMPTS = 20;

    /** 기본 닉네임 5자리 후보 수(D-164 (3)). 그래도 겹치면 TEMPORARILY_UNAVAILABLE. */
    private static final int FIVE_DIGIT_ATTEMPTS = 5;

    private static final int FIVE_DIGIT_START = 10_000;
    private static final int FIVE_DIGIT_RANGE = 90_000;

    private final MemberRepository memberRepository;
    private final List<ReservedNicknameChecker> reservedNicknameCheckers;
    private final TokenIssuer tokenIssuer;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom random;

    @Autowired
    public SignUpService(
            MemberRepository memberRepository,
            List<ReservedNicknameChecker> reservedNicknameCheckers,
            TokenIssuer tokenIssuer,
            AuditService auditService,
            Clock clock) {
        this(
                memberRepository,
                reservedNicknameCheckers,
                tokenIssuer,
                auditService,
                clock,
                new SecureRandom());
    }

    /** 시험은 숫자·바이트를 정해 둔 SecureRandom을 넘긴다. */
    SignUpService(
            MemberRepository memberRepository,
            List<ReservedNicknameChecker> reservedNicknameCheckers,
            TokenIssuer tokenIssuer,
            AuditService auditService,
            Clock clock,
            SecureRandom random) {
        this.memberRepository = memberRepository;
        this.reservedNicknameCheckers = List.copyOf(reservedNicknameCheckers);
        this.tokenIssuer = tokenIssuer;
        this.auditService = auditService;
        this.clock = clock;
        this.random = random;
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
        form.validate();
        Nickname nickname = settleNickname(form.nickname());
        Member member = memberRepository.save(Member.signUp(account, form, nickname, now));
        auditConsents(member.getId(), form.consents());
        return new SignUpResult(
                member.getId(), nickname.value(), tokenIssuer.issue(member.getId()));
    }

    /**
     * POST /members/guest(FR-197, D-89, D-128). 자격 증명 원문은 결과에 한 번만 싣고 저장·감사·로그에 남기지 않는다. 접속지별 생성
     * 한도는 web.auth AuthAttemptGuard가 이 연산을 부르기 전에 확인한다.
     */
    @Transactional
    public GuestStartResult startAsGuest(GuestStartForm form) {
        form.validate();
        Nickname nickname = numberedNickname();
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = memberRepository.save(Member.guest(nickname, form.consents(), now));
        GuestCredential.Issued issued = GuestCredential.issue(member.getId(), random);
        member.attachGuestCredential(issued.hash());
        memberRepository.save(member);
        auditConsents(member.getId(), form.consents());
        return new GuestStartResult(
                member.getId(),
                nickname.value(),
                tokenIssuer.issue(member.getId()),
                issued.token());
    }

    /**
     * 가입 닉네임(D-143): 제공자 이름을 Nickname.fromProviderName으로 고치고, 비면 기본 닉네임. 고친 이름이 겹치면 숫자 4자리를 붙여 5회까지
     * 다시 시도하고(Q-75) 그래도 겹치면 NICKNAME_TAKEN. NICKNAME_INVALID로 거부하지 않는다.
     */
    private Nickname settleNickname(String requested) {
        Optional<Nickname> fromProvider = Nickname.fromProviderName(requested);
        if (fromProvider.isEmpty()) {
            return numberedNickname();
        }
        Nickname base = fromProvider.get();
        if (!isTaken(base)) {
            return base;
        }
        for (int i = 0; i < MAX_SUFFIX_ATTEMPTS; i++) {
            Nickname candidate = base.withSuffix(random.nextInt(SUFFIX_BOUND));
            if (!isTaken(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException(ReasonCode.NICKNAME_TAKEN);
    }

    /**
     * 기본 닉네임(D-90, D-164 (3)): "러너" + 4자리를 다른 숫자로 20번, 그다음 5자리를 5번. 그래도 겹치면
     * TEMPORARILY_UNAVAILABLE(503).
     */
    private Nickname numberedNickname() {
        for (int i = 0; i < FOUR_DIGIT_ATTEMPTS; i++) {
            Nickname candidate = Nickname.numbered(random.nextInt(SUFFIX_BOUND));
            if (!isTaken(candidate)) {
                return candidate;
            }
        }
        for (int i = 0; i < FIVE_DIGIT_ATTEMPTS; i++) {
            Nickname candidate =
                    Nickname.numbered(FIVE_DIGIT_START + random.nextInt(FIVE_DIGIT_RANGE));
            if (!isTaken(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException(ReasonCode.TEMPORARILY_UNAVAILABLE);
    }

    private boolean isTaken(Nickname nickname) {
        return memberRepository.existsByNickname(nickname.value())
                || reservedNicknameCheckers.stream().anyMatch(c -> c.isReserved(nickname.value()));
    }

    /** 감사(가입동의, 행위자 RUNNER). 가입 때 받는 동의 항목만 적는다. */
    private void auditConsents(long memberId, Map<ConsentItem, Boolean> consents) {
        String id = String.valueOf(memberId);
        auditService.record(
                new AuditEntry(
                        id,
                        ActorRole.RUNNER,
                        AuditAction.SIGN_UP_CONSENT,
                        id,
                        null,
                        describe(consents)));
    }

    private static String describe(Map<ConsentItem, Boolean> consents) {
        return RequiredConsents.atSignUp(consents).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(","));
    }
}
