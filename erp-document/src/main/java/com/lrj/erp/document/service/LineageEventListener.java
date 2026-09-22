package com.lrj.erp.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.kernel.outbox.OutboxMessage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 从业务事件构建单据关系图。
 *
 * <p><b>为什么用事件而不是让业务模块直接调用</b>：MODULE_SERVICE_MAP 把
 * erp-document 的入向依赖限定为 app，消费方式是「全部业务事件」。
 * 采购、销售、财务各自直接调用溯源服务，会让 erp-document 被所有业务模块依赖，
 * 它就再也无法独立提取，关系图也会变成业务模块的隐式耦合点。
 *
 * <p>事件经 Outbox 投递，因此关系登记与业务写入不在同一事务——
 * 这是<b>有意</b>的：关系图是派生视图，短暂滞后可以接受；
 * 而把它拉进业务事务会让溯源故障阻断业务。审计则相反（必须同事务），
 * 二者的取舍差异见 AuditRecorder 的类注释。
 */
@Component
public class LineageEventListener {

    private final DocumentLineageService lineage;
    private final ObjectMapper objectMapper;

    public LineageEventListener(DocumentLineageService lineage, ObjectMapper objectMapper) {
        this.lineage = lineage;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理带上下游信息的业务事件。
     *
     * <p>约定：payload 含 {@code parentType/parentId/parentNo/childType/childId/childNo}
     * 的事件即视为一条关系声明。这样新增业务链路无需改本类——
     * 否则每加一种单据都要回头改溯源模块，那正是耦合。
     */
    @EventListener
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, timeout = 30)
    public void on(OutboxMessage message) {
        try {
            JsonNode p = objectMapper.readTree(message.payload());
            if (!p.hasNonNull("parentType") || !p.hasNonNull("childType")) {
                return;
            }
            lineage.link(message.tenantId(),
                    p.get("parentType").asText(), p.get("parentId").asText(),
                    p.path("parentNo").asText(null),
                    p.get("childType").asText(), p.get("childId").asText(),
                    p.path("childNo").asText(null));
        } catch (Exception e) {
            // 派生视图可以滞后，但不能静默丢失；独立事务失败后交给 Outbox 重试。
            // 不在错误摘要中拼入完整 payload，避免审计字段进入普通日志。
            throw new IllegalStateException("单据关系构建失败", e);
        }
    }
}
