package com.lrj.erp.kernel.outbox;

/**
 * 实际投递动作。MVP 为进程内分发；P8 接入 workflow-platform 时换成 Kafka 生产者，
 * 调度、重试与死信逻辑不必改动。
 *
 * <p>实现必须是<b>幂等安全</b>的：同一条消息可能因超时后结果未知而被重投。
 */
@FunctionalInterface
public interface OutboxDelivery {

    /** 投递失败请抛异常；返回即视为成功。 */
    void deliver(OutboxMessage message);
}
