package com.lrj.erp.kernel.outbox;

import java.time.OffsetDateTime;

/** Outbox 中的一条待投递消息。 */
public record OutboxMessage(
        long id,
        long tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        String status,
        int retryCount,
        OffsetDateTime nextRetryAt,
        String lastError) {

    public static final String PENDING = "PENDING";
    public static final String PUBLISHED = "PUBLISHED";
    /** 死信：重试已达上限，转人工处理。不再自动重试，但**不删除**——证据要留。 */
    public static final String DEAD = "DEAD";
}
