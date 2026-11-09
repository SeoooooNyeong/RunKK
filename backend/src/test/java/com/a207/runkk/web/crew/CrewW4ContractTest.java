package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.crew.CrewDetailView;
import com.a207.runkk.crew.CrewListRow;
import com.a207.runkk.crew.CrewListView;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.InvitePreviewView;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.MyCrewView;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * W4 REST 계약 표본 시험(S15P31A207-295, contract-W4.md). 서버가 쓰는 ObjectMapper로 응답 record를 직렬화해
 * src/test/resources/contract/crew/의 표본과 항목 이름·형식·값이 같은지 본다. 표본은 계약 문서와 앱(B단계) 파싱 시험이 함께 쓴다.
 */
@org.junit.jupiter.api.DisplayName("W4 크루 찾기·상세·초대·로고 응답 계약 표본")
class CrewW4ContractTest extends WebTestSupport {

    private static final TagSet DAWN = TagSet.of(Set.of(CrewTag.WEEKDAY_DAWN), TagOwner.CREW);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDateTime REJOIN = LocalDateTime.of(2026, 11, 10, 8, 15);

    private JsonNode sample(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contract/crew/" + name)) {
            assertThat(in).as(name).isNotNull();
            return json.readTree(in);
        }
    }

    private JsonNode serialize(Object response) throws IOException {
        return json.readTree(json.writeValueAsString(response));
    }

    private static CrewDetailView outsiderDetail() {
        return new CrewDetailView(
                12,
                "새벽러너스",
                "새벽에 함께 달려요",
                DAWN,
                CrewVisibility.PUBLIC,
                JoinPolicy.APPROVAL,
                "/api/v1/crew-logos/7",
                3,
                new DisplayName("달리는곰"),
                7,
                Distance.ofMeters(12_300),
                Distance.ofMeters(64_270),
                null,
                null,
                CREATED,
                Distance.ofMeters(1_284_670),
                5,
                List.of(
                        new CrewDetailView.RecentCrewRun(
                                88,
                                LocalDateTime.of(2026, 10, 21, 19, 30),
                                4,
                                Distance.ofMeters(20_150)),
                        new CrewDetailView.RecentCrewRun(
                                80,
                                LocalDateTime.of(2026, 10, 18, 7, 0),
                                3,
                                Distance.ofMeters(15_000))),
                REJOIN);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루 상세(크루 밖, 24시간 제한 중): 개설일·누적 거리·누적 크루런·최근 크루런·가입 가능 시각")
    void crewDetail_matchesSample() throws IOException {
        assertThat(serialize(CrewDtos.CrewDetailResponse.of(outsiderDetail())))
                .isEqualTo(sample("CrewDetailResponse-outsider.json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루 검색: totalCount")
    void crewList_matchesSample() throws IOException {
        CrewListRow row =
                new CrewListRow(
                        12,
                        "새벽러너스",
                        "새벽에 함께 달려요",
                        DAWN,
                        null,
                        7,
                        JoinPolicy.APPROVAL,
                        3,
                        Distance.ofMeters(12_300),
                        CREATED);
        CrewListView view =
                new CrewListView(
                        List.of(new CrewListView.Item(row, new DisplayName("달리는곰"))), 12L, 3);
        assertThat(serialize(CrewDtos.CrewListResponse.of(view)))
                .isEqualTo(sample("CrewListResponse.json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("내 크루(소속 없음, 24시간 제한 중): rejoinAvailableAt")
    void myCrew_matchesSample() throws IOException {
        assertThat(serialize(CrewDtos.MyCrewResponse.of(new MyCrewView(null, List.of(), REJOIN))))
                .isEqualTo(sample("MyCrewResponse-cooldown.json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("초대 코드 미리보기")
    void invitePreview_matchesSample() throws IOException {
        InvitePreviewView view =
                new InvitePreviewView(
                        12,
                        "새벽러너스",
                        "새벽에 함께 달려요",
                        DAWN,
                        null,
                        CrewVisibility.PRIVATE,
                        JoinPolicy.APPROVAL,
                        7,
                        CREATED);
        assertThat(serialize(CrewDtos.InvitePreviewResponse.of(view)))
                .isEqualTo(sample("InvitePreviewResponse.json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루 로고 업로드 결과")
    void crewLogo_matchesSample() throws IOException {
        assertThat(serialize(CrewDtos.CrewLogoResponse.of(7)))
                .isEqualTo(sample("CrewLogoResponse.json"));
    }
}
