package com.lrj.erp.masterdata.repository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 本位币属于主数据；业务模块不得跨表读取。 */
@Mapper
public interface CurrencyMapper {
    String findBaseCurrency(@Param("tenantId") long tenantId);
}
