package com.a207.runkk.crew;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 겹치지 않는 초대 코드를 만든다(설계 6.1.7). SecureRandom으로 6자리를 만들고 CrewRepository.existsByInviteCode가 참이면 다시
 * 만든다(최대 5회). 동시에 같은 코드가 나오면 고유 인덱스가 막는다(5.3).
 */
@Component
public class InviteCodeGenerator {

    /** 재시도 상한(5.3 "고유 인덱스 위반하면 다시 만들어 재시도(최대 5회)"). */
    static final int MAX_ATTEMPTS = 5;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 6;

    private final Predicate<String> taken;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public InviteCodeGenerator(CrewRepository crewRepository) {
        this(crewRepository::existsByInviteCode);
    }

    InviteCodeGenerator(Predicate<String> taken) {
        this.taken = taken;
    }

    /** 다섯 번 모두 겹치면 IllegalStateException. */
    public InviteCode issue(LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = next();
            if (!taken.test(code)) {
                return InviteCode.issue(code, now);
            }
        }
        throw new IllegalStateException("초대 코드를 만들지 못했다");
    }

    private String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
