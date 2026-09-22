package com.lrj.erp.kernel.outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 后台投递健康查询，不返回消息载荷。 */
@Service
public class OutboxHealthService {
    public record Health(long pending,long dead,double oldestSeconds) {}
    private final OutboxMapper mapper;
    public OutboxHealthService(OutboxMapper mapper) { this.mapper=mapper; }
    /** 有超时的只读汇总，避免监控请求占满连接池。 */
    @Transactional(readOnly=true,timeout=5)
    public Health read() { return mapper.health(); }
}
