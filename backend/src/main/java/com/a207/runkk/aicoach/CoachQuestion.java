package com.a207.runkk.aicoach;

import java.util.regex.Pattern;

public record CoachQuestion(String conversationId, String text) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    public CoachQuestion {
        if (conversationId != null && !validId(conversationId)) {
            throw new IllegalArgumentException("잘못된 대화 ID");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("빈 코치 질문");
        }
        text = text.strip();
        if (text.codePointCount(0, text.length()) > 500) {
            throw new CoachRejected(CoachRejected.Reason.QUESTION_TOO_LONG);
        }
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    String preview() {
        int length = Math.min(40, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, length));
    }

    @Override
    public String toString() {
        return "CoachQuestion[redacted]";
    }
}
