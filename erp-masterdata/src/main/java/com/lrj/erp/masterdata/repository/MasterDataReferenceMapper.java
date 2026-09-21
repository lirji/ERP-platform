package com.lrj.erp.masterdata.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 主数据引用登记持久层。 */
@Mapper
public interface MasterDataReferenceMapper {

    /**
     * 登记一次引用。同一张单据对同一条主数据重复引用不产生多行
     * （ON CONFLICT DO NOTHING + uk_md_reference）。
     */
    int register(@Param("tenantId") long tenantId,
                 @Param("mdType") String mdType,
                 @Param("mdId") long mdId,
                 @Param("businessType") String businessType,
                 @Param("businessId") String businessId);

    /** 该主数据是否已被任何单据引用。 */
    boolean isReferenced(@Param("tenantId") long tenantId,
                         @Param("mdType") String mdType,
                         @Param("mdId") long mdId);
}
