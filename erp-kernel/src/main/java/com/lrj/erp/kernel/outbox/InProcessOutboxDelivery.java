package com.lrj.erp.kernel.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MVP 的默认投递方式：<b>进程内</b>分发为 Spring 应用事件。
 *
 * <p>TECH_SELECTION 否决了 MVP 引入 MQ（"不要为了 MQ 而 MQ"）：当前只有一个进程，
 * 跨模块协作不需要跨进程传输。但事件<b>登记</b>仍然走 Outbox，
 * 因为要解决的是"业务提交后可靠触发下游"，那是事务问题，不是传输问题。
 *
 * <p>P8 接入 workflow-platform 时，把这个 Bean 换成 Kafka 生产者即可，
 * 调度、重试、退避与死信逻辑完全不用改——这正是把投递抽象成
 * {@link OutboxDelivery} 的目的。
 */
@Configuration
public class InProcessOutboxDelivery {

    /** {@code @ConditionalOnMissingBean}：测试或后续阶段可用自己的实现覆盖。 */
    @Bean
    @ConditionalOnMissingBean(OutboxDelivery.class)
    public OutboxDelivery inProcessDelivery(ApplicationEventPublisher publisher) {
        return publisher::publishEvent;
    }
}
