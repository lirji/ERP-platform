package com.lrj.erp.app.monitoring;
import com.lrj.erp.kernel.monitoring.IntegrityProbe;
import com.lrj.erp.kernel.outbox.OutboxHealthService;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/** 低基数运维指标。对账每次有界扫描，完整一轮结束后才发布计数；未知不伪报为零。 */
@Component
public class BusinessHealthMetrics {
    private static final Logger LOG=LoggerFactory.getLogger(BusinessHealthMetrics.class);
    private static final int BATCH=1000;
    private final OutboxHealthService outbox;
    private final MeterRegistry meters;
    private final Map<IntegrityProbe,Progress> progress=new LinkedHashMap<>();
    private volatile double pending=Double.NaN,dead=Double.NaN,oldest=Double.NaN;
    private volatile long outboxSample;
    private static class Progress {
        long cursor,running;
        volatile double published=Double.NaN;
        volatile long completed;
    }
    public BusinessHealthMetrics(OutboxHealthService outbox,List<IntegrityProbe> probes,MeterRegistry meters) {
        this.outbox=outbox;this.meters=meters;
        Gauge.builder("erp.outbox.pending",this,x->x.pending).register(meters);
        Gauge.builder("erp.outbox.dead",this,x->x.dead).register(meters);
        Gauge.builder("erp.outbox.oldest.seconds",this,x->x.oldest).register(meters);
        Gauge.builder("erp.outbox.sample.age.seconds",this,x->age(x.outboxSample)).register(meters);
        Set<String> names=new HashSet<>();
        for(var probe:probes) {
            if(!names.add(probe.name())) throw new IllegalStateException("对账指标名称重复");
            Progress state=new Progress();progress.put(probe,state);
            Gauge.builder("erp.integrity.mismatches",state,x->x.published).tag("domain",probe.name()).register(meters);
            Gauge.builder("erp.integrity.completed.age.seconds",state,x->age(x.completed)).tag("domain",probe.name()).register(meters);
        }
    }
    /** 无租户高基数标签，跨租户汇总仅暴露计数；每个探针有自己的失败计数及陈旧时长。 */
    public synchronized void refresh() {
        try {
            var health=outbox.read();pending=health.pending();dead=health.dead();oldest=health.oldestSeconds();outboxSample=System.nanoTime();
        } catch(RuntimeException e) { failed("outbox",e); }
        for(var entry:progress.entrySet()) {
            var probe=entry.getKey();var state=entry.getValue();
            try {
                var batch=probe.scan(state.cursor,BATCH);
                if(batch.rows()==0) {
                    state.published=state.running;state.completed=System.nanoTime();state.running=0;state.cursor=0;
                } else { state.running+=batch.mismatches();state.cursor=batch.cursor(); }
            } catch(RuntimeException e) { failed(probe.name(),e); }
        }
    }
    private void failed(String probe,RuntimeException error) {
        meters.counter("erp.health.probe.errors","probe",probe).increment();
        // 不把数据库异常全文、SQL绑定参数和财务载荷写到普通日志。
        LOG.warn("health probe failed probe={} errorType={} runbook=docs/operations/OBSERVABILITY.md",probe,error.getClass().getSimpleName());
    }
    private static double age(long completed) { return completed==0?Double.NaN:(System.nanoTime()-completed)/1_000_000_000.0; }
}
