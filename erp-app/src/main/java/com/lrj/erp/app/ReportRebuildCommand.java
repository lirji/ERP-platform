package com.lrj.erp.app;
import com.lrj.erp.reporting.application.ReportSnapshotService;
import com.lrj.erp.kernel.reporting.ReportSource.Kind;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 显式启用的本地管理命令；无 HTTP 入口，按持久任务 ID 可恢复。 */
@Component
@ConditionalOnProperty(name="erp.reporting.cli",havingValue="true")
public class ReportRebuildCommand implements ApplicationRunner {
    private static final Logger LOG=LoggerFactory.getLogger(ReportRebuildCommand.class);
    private final ReportSnapshotService reports;
    private final Environment environment;
    private final ConfigurableApplicationContext context;
    public ReportRebuildCommand(ReportSnapshotService reports,Environment environment,ConfigurableApplicationContext context) {
        this.reports=reports;this.environment=environment;this.context=context;
    }
    @Override public void run(ApplicationArguments args) {
        long tenant=environment.getRequiredProperty("erp.reporting.tenant",Long.class);
        Kind kind=Kind.valueOf(environment.getRequiredProperty("erp.reporting.kind"));
        long id=environment.getProperty("erp.reporting.job",Long.class,0L);
        if(id==0) id=reports.start(tenant,kind);
        if(!reports.job(tenant,id).kind().equals(kind.code())) throw new IllegalArgumentException("恢复任务类型不匹配");
        LOG.info("report rebuild tenantId={} kind={} jobId={}",tenant,kind,id);
        for(int step=0;step<100_000;step++) {
            var job=reports.step(tenant,id,1000);
            if(step%100==0) LOG.info("report checkpoint jobId={} cursor={} state={}",id,job.cursorId(),job.state());
            if(!job.state().equals("BUILDING")) {
                if(!job.state().equals("PUBLISHED")) throw new IllegalStateException("来源在重建期间变化，候选未发布；重新启动新任务");
                LOG.info("report published tenantId={} kind={} snapshotId={}",tenant,kind,id);
                context.close();return;
            }
        }
        throw new IllegalStateException("本次批次数上限已到，请按日志任务 ID 恢复");
    }
}
