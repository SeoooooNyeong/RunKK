package com.a207.runkk.aimodel;

public interface AiModelGateway {
    AiModel.Catalog list();

    AiModel register(String id, String url, String note);

    AiModel activate(String id);

    AiModel rollback();
}
