package com.a207.runkk.aiprivacy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiPurgeRequestRepository {
    Optional<AiPurgeRequest> findForUpdate(long memberId);

    void save(AiPurgeRequest request);

    List<Long> dueMembers(LocalDateTime now);
}
