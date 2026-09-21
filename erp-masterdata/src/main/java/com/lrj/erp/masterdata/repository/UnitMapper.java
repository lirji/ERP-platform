package com.lrj.erp.masterdata.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 计量单位持久层。 */
@Mapper
public interface UnitMapper {

    int insert(@Param("tenantId") long tenantId, @Param("code") String code,
               @Param("name") String name);

    /** 单位是否存在且启用；新 SKU 不得引用已停用的单位。 */
    boolean existsEnabled(@Param("tenantId") long tenantId, @Param("id") long id);

    String findName(@Param("tenantId") long tenantId, @Param("id") long id);

    int setEnabled(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("enabled") boolean enabled);
}
