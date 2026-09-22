package com.lrj.erp.finance.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.finance.domain.BillType;
import com.lrj.erp.kernel.events.ReturnPosted;
import com.lrj.erp.kernel.outbox.OutboxMessage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** 退货与原收发货事件可乱序到达；缺失原账时失败重试，独立事务防止批次污染。 */
@Component
public class FinancialReturnListener {
    private final CreditAdjustmentService service;
    private final ObjectMapper json;
    public FinancialReturnListener(CreditAdjustmentService service,ObjectMapper json) { this.service=service;this.json=json; }
    @EventListener
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=30)
    public void on(OutboxMessage message) {
        BillType type;
        if(ReturnPosted.PURCHASE.equals(message.eventType())) type=BillType.AP;
        else if(ReturnPosted.SALES.equals(message.eventType())) type=BillType.AR;
        else return;
        ReturnPosted event;
        try { event=json.readValue(message.payload(),ReturnPosted.class); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("退货事件载荷不合法",e); }
        if(!event.returnId().equals(message.aggregateId())) throw new IllegalArgumentException("退货事件聚合不匹配");
        service.accept(message.tenantId(),type,event);
    }
}
