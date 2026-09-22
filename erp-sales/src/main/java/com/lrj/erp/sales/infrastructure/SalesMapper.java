package com.lrj.erp.sales.infrastructure;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lrj.erp.sales.domain.SalesRepository.OrderHeader;
import com.lrj.erp.sales.domain.SalesRepository.OrderLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售持久层。SQL 见 resources/mapper/SalesMapper.xml。
 *
 * <p>写入方法用 {@code INSERT ... RETURNING id}，在 MyBatis 中以 {@code <select>} 声明，
 * 会被 MP 拦截器按 Select 交给 JSqlParser 解析并抛 UnsupportedOperationException，
 * 且解析发生在 handler 之前。因此只在写入方法上逐个关闭拦截——它们的 tenant_id 由调用方显式传入。
 * <b>查询方法一律保留拦截</b>：sal_order 在数据权限允许列表内，整个 Mapper 关闭就是越权。
 */
@Mapper
public interface SalesMapper {

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertOrder(@Param("tenantId") long tenantId, @Param("companyId") long companyId,
                     @Param("orderNo") String orderNo, @Param("customerId") long customerId,
                     @Param("customerRef") String customerRef, @Param("warehouseId") long warehouseId,
                     @Param("totalAmount") BigDecimal totalAmount,
                     @Param("orgPath") String orgPath, @Param("createdBy") long createdBy);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertOrderLine(@Param("tenantId") long tenantId, @Param("orderId") long orderId,
                         @Param("lineNo") int lineNo, @Param("skuId") long skuId,
                         @Param("skuRef") String skuRef, @Param("batchNo") String batchNo,
                         @Param("orderedQty") BigDecimal orderedQty,
                         @Param("unitPrice") BigDecimal unitPrice);

    /** 订单是销售用例的并发锁入口。 */
    OrderHeader lockOrder(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    int bindCreditApproval(@Param("tenantId") long tenantId, @Param("orderId") long orderId,
                           @Param("approvalId") long approvalId);

    OrderHeader findOrder(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    List<OrderLine> findOrderLines(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    OrderLine findOrderLine(@Param("tenantId") long tenantId, @Param("orderLineId") long orderLineId);

    int increaseShippedIfWithinOrdered(@Param("tenantId") long tenantId,
                                       @Param("orderLineId") long orderLineId,
                                       @Param("qty") BigDecimal qty);

    int updateOrderState(@Param("tenantId") long tenantId, @Param("orderId") long orderId,
                         @Param("state") String state, @Param("newVersion") long newVersion);

    int markReserved(@Param("tenantId") long tenantId, @Param("orderId") long orderId,
                     @Param("reserved") boolean reserved);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertShipment(@Param("tenantId") long tenantId, @Param("companyId") long companyId,
                        @Param("shipmentNo") String shipmentNo, @Param("orderId") long orderId,
                        @Param("warehouseId") long warehouseId, @Param("orgPath") String orgPath,
                        @Param("createdBy") long createdBy);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertShipmentLine(@Param("tenantId") long tenantId, @Param("shipmentId") long shipmentId,
                            @Param("orderLineId") long orderLineId, @Param("skuId") long skuId,
                            @Param("batchNo") String batchNo, @Param("qty") BigDecimal qty);

    int markDelivered(@Param("tenantId") long tenantId, @Param("shipmentId") long shipmentId);

    int markSigned(@Param("tenantId") long tenantId, @Param("shipmentId") long shipmentId);

    boolean hasAnyShipment(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    int ensureCreditAccount(@Param("tenantId") long tenantId, @Param("customerId") long customerId);

    int occupyCreditIfWithinLimit(@Param("tenantId") long tenantId, @Param("customerId") long customerId,
                                  @Param("amount") BigDecimal amount,
                                  @Param("creditLimit") BigDecimal creditLimit);

    int occupyCreditForced(@Param("tenantId") long tenantId, @Param("customerId") long customerId,
                           @Param("amount") BigDecimal amount);

    int releaseCredit(@Param("tenantId") long tenantId, @Param("customerId") long customerId,
                      @Param("amount") BigDecimal amount);

    BigDecimal usedCredit(@Param("tenantId") long tenantId, @Param("customerId") long customerId);
}
