package com.lrj.erp.procurement.infrastructure;

import com.lrj.erp.procurement.domain.ProcurementRepository.OrderHeader;
import com.lrj.erp.procurement.domain.ProcurementRepository.OrderLine;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购持久层。SQL 见 resources/mapper/ProcurementMapper.xml。
 *
 * <p><b>关于 @InterceptorIgnore 的用法</b>：写入方法用 {@code INSERT ... RETURNING id}，
 * 在 MyBatis 中以 {@code <select>} 声明（PostgreSQL 取自增主键的常见写法）。
 * MP 的拦截器会把所有 select 交给 JSqlParser 按 Select 解析，遇到这种形态抛
 * UnsupportedOperationException，且解析发生在 handler 之前。
 *
 * <p>因此只在<b>写入方法</b>上逐个关闭拦截——它们的 tenant_id 由调用方显式传入。
 * <b>查询方法一律保留拦截</b>：pur_order 在数据权限允许列表内，
 * 若整个 Mapper 关闭，采购单查询就会失去租户与数据权限过滤，那是越权。
 */
@Mapper
public interface ProcurementMapper {

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertOrder(@Param("tenantId") long tenantId, @Param("companyId") long companyId,
                     @Param("orderNo") String orderNo, @Param("supplierId") long supplierId,
                     @Param("supplierRef") String supplierRef, @Param("warehouseId") long warehouseId,
                     @Param("orgPath") String orgPath, @Param("createdBy") long createdBy);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertOrderLine(@Param("tenantId") long tenantId, @Param("orderId") long orderId,
                         @Param("lineNo") int lineNo, @Param("skuId") long skuId,
                         @Param("skuRef") String skuRef, @Param("orderedQty") BigDecimal orderedQty,
                         @Param("unitPrice") BigDecimal unitPrice);

    OrderHeader findOrder(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    List<OrderLine> findOrderLines(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    OrderLine findOrderLine(@Param("tenantId") long tenantId, @Param("orderLineId") long orderLineId);

    int increaseReceivedIfWithinOrdered(@Param("tenantId") long tenantId,
                                        @Param("orderLineId") long orderLineId,
                                        @Param("qty") BigDecimal qty);

    int closeOrderLine(@Param("tenantId") long tenantId, @Param("orderLineId") long orderLineId);

    int updateOrderState(@Param("tenantId") long tenantId, @Param("orderId") long orderId,
                         @Param("state") String state, @Param("newVersion") long newVersion);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertReceipt(@Param("tenantId") long tenantId, @Param("companyId") long companyId,
                       @Param("receiptNo") String receiptNo, @Param("orderId") long orderId,
                       @Param("warehouseId") long warehouseId, @Param("orgPath") String orgPath,
                       @Param("createdBy") long createdBy);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertReceiptLine(@Param("tenantId") long tenantId, @Param("receiptId") long receiptId,
                           @Param("orderLineId") long orderLineId, @Param("skuId") long skuId,
                           @Param("batchNo") String batchNo, @Param("qty") BigDecimal qty);

    boolean hasAnyReceipt(@Param("tenantId") long tenantId, @Param("orderId") long orderId);
}
