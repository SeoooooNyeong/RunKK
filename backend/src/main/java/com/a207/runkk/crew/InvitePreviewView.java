package com.a207.runkk.crew;

import com.a207.runkk.common.TagSet;
import java.time.LocalDateTime;

/**
 * 초대 코드로 찾은 크루의 요약(피그마 F-03c, S15P31A207-295). 가입 전에 보여 주는 확인 단계라 비공개 크루도 담는다(초대 코드가 비공개 크루의 가입
 * 수단이다). 초대 코드·크루장·거리 같은 크루 안 정보는 담지 않는다.
 *
 * @param crewId 크루 ID
 * @param name 크루명
 * @param intro 한줄소개
 * @param tags 크루 태그
 * @param logoUrl 로고, 없으면 null
 * @param visibility 공개 여부(비공개면 앱이 상세 이동을 숨긴다)
 * @param joinPolicy 가입 방식(초대 코드 가입은 방식과 상관없이 바로 가입)
 * @param headcount 인원
 * @param createdAt 개설 일시
 */
public record InvitePreviewView(
        long crewId,
        String name,
        String intro,
        TagSet tags,
        String logoUrl,
        CrewVisibility visibility,
        JoinPolicy joinPolicy,
        int headcount,
        LocalDateTime createdAt) {

    static InvitePreviewView of(Crew crew) {
        return new InvitePreviewView(
                crew.getId(),
                crew.name().value(),
                crew.intro(),
                crew.tags(),
                crew.logoUrl(),
                crew.visibility(),
                crew.joinPolicy(),
                crew.headcount(),
                crew.createdAt());
    }
}
