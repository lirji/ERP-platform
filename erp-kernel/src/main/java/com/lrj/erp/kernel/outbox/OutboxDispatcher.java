package com.lrj.erp.kernel.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 投递调度器：领取待发消息，投递，成功标记已发，失败退避重试，超限转死信。
 *
 * <p>重试是<b>有界</b>的（开发规范 §设计 9）：次数上限 + 线性退避。
 * 无上限重试会在下游故障时把它彻底打死，并让积压永远清不掉。
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxMapper mapper;
    private final OutboxDelivery delivery;
    private final int batchSize;
    private final int maxRetry;
    private final int backoffSeconds;

    public OutboxDispatcher(OutboxMapper mapper,
                            OutboxDelivery delivery,
                            @Value("${erp.outbox.batch-size:100}") int batchSize,
                            @Value("${erp.outbox.max-retry:5}") int maxRetry,
                            @Value("${erp.outbox.backoff-seconds:10}") int backoffSeconds) {
        this.mapper = mapper;
        this.delivery = delivery;
        this.batchSize = batchSize;
        this.maxRetry = maxRetry;
        this.backoffSeconds = backoffSeconds;
    }

    /**
     * 投递一批。返回成功投递的条数。
     *
     * <p>整批放在一个事务里：claimPending 的 {@code FOR UPDATE SKIP LOCKED} 行锁
     * 必须持续到状态更新完成，否则另一个实例会重复领取同一批。
     */
    @Transactional
    public int dispatchBatch() {
        List<OutboxMessage> batch = mapper.claimPending(batchSize);
        int published = 0;
        for (OutboxMessage message : batch) {
            try {
                delivery.deliver(message);
                mapper.markPublished(message.id());
                published++;
            } catch (Exception e) {
                // 单条失败不影响同批其他消息——否则一条毒消息会卡住整个队列
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                mapper.markFailed(message.id(), maxRetry, backoffSeconds, truncate(reason));
                log.warn("Outbox 投递失败 id={} eventType={} retry={} reason={}",
                        message.id(), message.eventType(), message.retryCount() + 1, reason);
            }
        }
        return published;
    }

    /** 错误摘要要有界，否则一个超长异常信息会把这一列撑爆。 */
    private static String truncate(String s) {
        return s == null ? null : (s.length() <= 1000 ? s : s.substring(0, 1000));
    }
}
