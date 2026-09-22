package com.lrj.erp.app.outbox;
import com.lrj.erp.kernel.outbox.OutboxDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 本地单进程可靠事件轮询；固定延迟避免上批未结束时无限积压任务。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="erp.outbox.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class OutboxScheduling {
    private final OutboxDispatcher dispatcher;
    public OutboxScheduling(OutboxDispatcher dispatcher,
                            @org.springframework.beans.factory.annotation.Value("${erp.outbox.poll-interval-ms:1000}") long interval) {
        if (interval < 100 || interval > 60000) throw new IllegalArgumentException("Outbox 轮询间隔必须在 100–60000 毫秒之间");
        this.dispatcher=dispatcher;
    }
    /** 每批沿用 Outbox 有界领取及重试机制。 */
    @Scheduled(fixedDelayString="${erp.outbox.poll-interval-ms:1000}",initialDelayString="${erp.outbox.poll-interval-ms:1000}")
    public void dispatch(){dispatcher.dispatchBatch();}
}
