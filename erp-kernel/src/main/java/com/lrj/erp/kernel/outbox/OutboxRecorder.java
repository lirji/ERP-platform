package com.lrj.erp.kernel.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在业务事务内登记待发事件。
 *
 * <p>这是 Outbox 的全部意义：事件与业务数据<b>同一个本地事务</b>落库，
 * 因此不存在"业务提交成功但事件丢失"或"事件已发但业务回滚"。
 * 投递由独立调度器异步完成。
 *
 * <p>{@code MANDATORY} 传播：必须在调用方的事务中执行。若调用方忘了开事务，
 * 立刻报错而不是悄悄新开一个——后者会让事件与业务数据分属两个事务，
 * Outbox 的保证当场失效，而且极难发现。
 */
@Service
public class OutboxRecorder {

    private final OutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(long tenantId, String aggregateType, String aggregateId,
                       String eventType, Object payload) {
        try {
            mapper.append(new OutboxMessage(0L, tenantId, aggregateType, aggregateId, eventType,
                    objectMapper.writeValueAsString(payload),
                    OutboxMessage.PENDING, 0, null, null));
        } catch (JsonProcessingException e) {
            // 序列化失败必须让业务失败：发不出去的事件 = 下游永远不知道这件事发生过
            throw new IllegalArgumentException("事件载荷无法序列化: " + eventType, e);
        }
    }
}
