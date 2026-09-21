package com.lrj.erp.approval.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 审批实例持久层。 */
@Mapper
public interface ApprovalInstanceMapper {

    /** 提交；同一业务单据已有 PENDING 实例时不插入（由部分唯一索引保证）。 */
    int insertPending(@Param("tenantId") long tenantId,
                      @Param("businessType") String businessType,
                      @Param("businessId") String businessId,
                      @Param("documentNo") String documentNo,
                      @Param("submittedBy") long submittedBy);

    Long findPendingId(@Param("tenantId") long tenantId,
                       @Param("businessType") String businessType,
                       @Param("businessId") String businessId);

    String findLatestStatus(@Param("tenantId") long tenantId,
                            @Param("businessType") String businessType,
                            @Param("businessId") String businessId);

    /** 仅当仍为 PENDING 时才落决策；影响 0 行表示实例已结束。 */
    int decide(@Param("tenantId") long tenantId, @Param("id") long id,
               @Param("status") String status, @Param("decidedBy") long decidedBy,
               @Param("reason") String reason);
}
