package com.lrj.erp.document.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 审计日志持久层。SQL 见 resources/mapper/AuditLogMapper.xml。 */
@Mapper
public interface AuditLogMapper {

    /**
     * 按业务类型查审计记录。
     *
     * <p>SQL 里<b>刻意不写</b>租户与数据权限条件——它们由拦截器统一注入。
     * 若每个 Mapper 各写一遍，漏写一处就是一个越权漏洞，而且无法被机器检查。
     */
    List<Map<String, Object>> findByBusinessType(@Param("businessType") String businessType);

    int insert(@Param("tenantId") long tenantId,
               @Param("userId") long userId,
               @Param("businessType") String businessType,
               @Param("businessId") String businessId,
               @Param("action") String action,
               @Param("companyId") long companyId,
               @Param("orgPath") String orgPath,
               @Param("createdBy") long createdBy);
}
