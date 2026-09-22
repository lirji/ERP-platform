package com.lrj.erp.finance.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.finance.domain.BillType;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.kernel.outbox.OutboxMessage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 独立事务避免一条坏消息使整个 Outbox 批次处于 rollback-only；失败继续向投递器抛出。 */
@Component
public class FinancialPostingListener {
    private final SettlementService service; private final ObjectMapper json;
    public FinancialPostingListener(SettlementService service,ObjectMapper json){this.service=service;this.json=json;}
    /** 仅消费完整 v2 财务事实；历史 v1 缺金额，不能凭空补算。 */
    @EventListener
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=30)
    public void on(OutboxMessage message) {
        BillType type;
        if(PostedDocument.PURCHASE.equals(message.eventType()))type=BillType.AP;
        else if(PostedDocument.SALES.equals(message.eventType()))type=BillType.AR;
        else return;
        PostedDocument event;
        try { event=json.readValue(message.payload(),PostedDocument.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 返回可诊断的固定摘要，避免 Spring 对 checked exception 的包装输出完整财务载荷。
            throw new IllegalArgumentException("财务事件载荷不合法", e);
        }
        if(!event.childId().equals(message.aggregateId()))throw new IllegalArgumentException("财务事件聚合 ID 不匹配");
        service.acceptPosted(message.tenantId(),type,event);
    }
}
