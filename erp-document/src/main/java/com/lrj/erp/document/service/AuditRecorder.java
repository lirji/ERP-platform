package com.lrj.erp.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.document.repository.AuditLogMapper;
import com.lrj.erp.kernel.context.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 业务审计写入（CAP-P09）。
 *
 * <p><b>与业务写入同事务</b>：审计若异步落库，业务提交而审计丢失时，
 * 系统会声称"没人改过"——审计一旦可能丢，它的证据价值就没有了。
 * 这也是审计不走 Outbox 的原因（Outbox 保证最终送达，但审计要的是"与事实同生共死"）。
 *
 * <p>本服务被 AOP 与业务代码共同使用：能自动捕获的（谁、何时、来源 IP、traceId）
 * 由这里统一取，只有"改了什么"必须由调用方给——只有调用方知道实体的形状。
 */
@Service
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    /** 落库前需要脱敏的字段名（SECURITY_ARCHITECTURE 的脱敏规则在此落地）。 */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "bankAccount", "idCard", "idNumber", "mobile", "phone");

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditRecorder(AuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一次业务操作。
     *
     * @param before 修改前的值；新建时为 null
     * @param after  修改后的值；删除时为 null
     */
    @Transactional
    public void record(String businessType, String businessId, String documentNo,
                       String action, Object before, Object after) {
        AccessContext ctx = AccessContextHolder.require();
        RequestMetadata meta = RequestMetadataHolder.current();

        mapper.insertAudit(
                ctx.tenantId(), ctx.userId(), businessType, businessId, documentNo, action,
                toJson(before), toJson(after),
                meta.sourceIp(), meta.userAgent(), MDC.get("traceId"),
                ctx.companyId(), ctx.orgPath(), ctx.userId());
    }

    /**
     * 序列化并脱敏。序列化失败<b>不得</b>让业务失败——但必须留痕，
     * 否则会出现"审计静默缺失"这种最糟糕的情况：既没有记录，也没人知道缺了。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            var node = objectMapper.valueToTree(value);
            if (node.isObject()) {
                var obj = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                SENSITIVE_KEYS.forEach(k -> {
                    if (obj.has(k)) {
                        obj.put(k, "***");
                    }
                });
            }
            return objectMapper.writeValueAsString(node);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            log.warn("审计值序列化失败，将以占位符记录；business value type={}",
                    value.getClass().getName(), e);
            return "{\"_serializationFailed\":true}";
        }
    }
}
