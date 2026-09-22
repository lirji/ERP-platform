package com.lrj.erp.app.monitoring;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
/** 可独立关闭的后台监控；固定延迟防止本任务重叠。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="erp.monitoring.enabled",havingValue="true",matchIfMissing=true)
public class HealthMonitoringSchedule {
    private final BusinessHealthMetrics metrics;
    public HealthMonitoringSchedule(BusinessHealthMetrics metrics,
            @org.springframework.beans.factory.annotation.Value("${erp.monitoring.interval-ms:1000}") long interval) {
        if(interval<100 || interval>60000) throw new IllegalArgumentException("监控间隔须为100–60000ms");
        this.metrics=metrics;
    }
    /** 每次三个各1000行的探针，避免一次完整对账长期占用数据库连接。 */
    @Scheduled(fixedDelayString="${erp.monitoring.interval-ms:1000}")
    public void sample() { metrics.refresh(); }
}
