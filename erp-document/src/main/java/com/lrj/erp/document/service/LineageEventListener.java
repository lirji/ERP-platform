package com.lrj.erp.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.kernel.outbox.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LineageEventListener.class);

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
            // 关系图是派生视图：构建失败不得影响业务，但必须留痕，
            // 否则会出现"溯源静默缺失"——查不到链路时没人知道是没发生还是没记上
            log.warn("构建单据关系失败 eventType={} aggregateId={}",
                    message.eventType(), message.aggregateId(), e);
        }
    }
}
