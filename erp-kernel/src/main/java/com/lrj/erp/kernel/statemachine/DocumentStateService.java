package com.lrj.erp.kernel.statemachine;

import com.lrj.erp.kernel.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 状态迁移的统一入口：校验合法性 → 抢占版本 → 留痕。
 *
 * <p>业务模块调用本服务完成迁移，而不是各自写 {@code if (status == ...)}。
 * 并发保护不靠应用层判断，而是靠 {@code doc_state_transition} 上
 * {@code (tenant, type, id, from_version)} 的唯一索引：
 * 同一版本只能被迁移一次，并发时数据库决定谁赢。
 */
@Service
public class DocumentStateService {

    private final StateTransitionMapper mapper;

    private final com.lrj.erp.kernel.monitoring.BusinessAudit audit;

    public DocumentStateService(StateTransitionMapper mapper,com.lrj.erp.kernel.monitoring.BusinessAudit audit) {
        this.audit=audit;
        this.mapper = mapper;
    }

    /**
     * 执行一次迁移并留痕。
     *
     * @param fromVersion 调用方读到的单据版本；迁移成功后业务表应更新为 fromVersion + 1
     * @return 目标状态
     * @throws DomainException {@code ERP-DOC-2001} 非法迁移 · {@code ERP-DOC-3001} 守卫拒绝
     *                         · {@code ERP-DOC-2002} 并发冲突（同版本已被他人迁移）
     */
    @Transactional
    public <T> DocumentState transition(StateMachine<T> machine,
                                        T subject,
                                        DocumentState current,
                                        DocumentEvent event,
                                        long tenantId,
                                        String businessType,
                                        String businessId,
                                        String documentNo,
                                        long fromVersion,
                                        long operatorId,
                                        String traceId) {

        // 1) 状态机先判定：非法迁移与守卫拒绝在这里被区分开（2001 vs 3001）
        DocumentState next = machine.fire(current, event, subject);

        // 2) 抢占版本：并发下只有一个线程能插入成功
        int inserted = mapper.insertIfVersionUnused(new StateTransitionRecord(
                tenantId, businessType, businessId, documentNo,
                current.code(), next.code(), event.code(),
                fromVersion, operatorId, traceId));

        if (inserted == 0) {
            throw new DomainException(DocumentErrorCode.CONCURRENT_MODIFY,
                    Map.of("businessType", businessType,
                           "businessId", businessId,
                           "fromVersion", fromVersion));
        }
        audit.record(tenantId,operatorId,businessType,businessId,documentNo,event.code());
        return next;
    }

    /** 某张单据的完整流转历史，按时间正序。 */
    public List<StateTransitionRecord> history(long tenantId, String businessType, String businessId) {
        return mapper.findHistory(tenantId, businessType, businessId);
    }
}
