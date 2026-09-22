package com.lrj.erp.inventory.infrastructure;
import com.lrj.erp.inventory.domain.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;

/** 库存作业 SQL 集中于同名 XML。 */
@Mapper
public interface StockOperationMapper {
    /** INSERT RETURNING 仅此写入绕过 Select 解析；租户字段显式绑定，查询仍保留拦截。 */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true", dataPermission="true")
    long insert(@Param("o") StockOperation operation);
    StockOperation lock(@Param("tenantId") long tenantId, @Param("id") long id);
    int transition(@Param("o") StockOperation operation, @Param("next") String next);
    int freeze(@Param("b") InventoryBucket bucket, @Param("id") long id);
    int unfreeze(@Param("b") InventoryBucket bucket, @Param("id") long id);
    int recordCount(@Param("o") StockOperation operation, @Param("difference") BigDecimal difference);
    int recordValue(@Param("o") StockOperation operation, @Param("value") BigDecimal value);
    int transit(@Param("b") InventoryBucket bucket, @Param("delta") BigDecimal delta);
}
