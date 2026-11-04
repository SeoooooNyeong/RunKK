package com.a207.runkk.avatar;

import java.util.Optional;

public interface SelfieJobRepository {
    Optional<SelfieJob> find(String requestId);

    Optional<SelfieJob> byJob(long memberId, String jobId);

    Optional<SelfieJob> pending(long memberId);

    void save(SelfieJob job);

    void deleteByMember(long memberId);
}
