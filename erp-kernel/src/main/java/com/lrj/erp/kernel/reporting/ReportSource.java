package com.lrj.erp.kernel.reporting;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 权威模块导出边界；报表依赖契约，不跨模块查业务表。所有金额为本位币，NULL 表示历史未知。 */
public interface ReportSource {
    /** 封闭的五类报表，持久化使用显式稳定 code。 */
    enum Kind {
        INVENTORY("INVENTORY"), PURCHASE("PURCHASE"), SALES("SALES"), AR("AR"), AP("AP");
        private final String code;
        Kind(String code) { this.code=code; }
        public String code() { return code; }
    }
    /** 同一来源粒度事实。未适用的维度用零/空，金额未知保留 NULL，不以零替代。 */
    record Row(long sourceId,long companyId,long partnerId,long warehouseId,long skuId,String batchNo,
            LocalDate businessDate,BigDecimal quantity,BigDecimal reserved,BigDecimal locked,BigDecimal inTransit,
            BigDecimal amount,BigDecimal reduction,BigDecimal settled,BigDecimal paid) {}
    Kind kind();
    /** 只要对报表有意义的事实变化，本指纹就应变化；对应权威模块受控写入路径。 */
    String fingerprint(long tenantId);
    /** 稳定主键游标分页，最多一千行，禁止 offset 全表翻页。 */
    List<Row> page(long tenantId,long afterId,int limit);
    /** 端口也校验边界，避免绕过报表服务造成无界扫描。 */
    static void validate(long tenantId,long afterId,int limit) {
        if(tenantId<=0 || afterId<0 || limit<1 || limit>1000) throw new IllegalArgumentException("报表租户或分页参数非法");
    }
}
