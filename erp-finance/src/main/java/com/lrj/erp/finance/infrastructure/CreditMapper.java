package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.finance.domain.*;
import com.lrj.erp.finance.domain.CreditRepository.*;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.math.BigDecimal;
/** INSERT RETURNING 显式绑定租户；只对该写入关闭 Select 解析，查询保留拦截。 */
@Mapper
public interface CreditMapper {
    Credit findSource(@Param("tenantId") long tenantId,@Param("returnType") String returnType,@Param("returnId") String returnId);
    Credit find(@Param("tenantId") long tenantId,@Param("id") long id);
    int addCredit(@Param("tenantId") long tenantId,@Param("type") BillType type,@Param("billId") long billId,@Param("amount") BigDecimal amount);
    @InterceptorIgnore(tenantLine="true", dataPermission="true")
    long insert(@Param("c") Credit credit,@Param("operator") long operator);
    Refund findCommand(@Param("tenantId") long tenantId,@Param("commandId") String commandId);
    int addRefund(@Param("tenantId") long tenantId,@Param("id") long id,@Param("amount") BigDecimal amount);
    @InterceptorIgnore(tenantLine="true", dataPermission="true")
    long insertRefund(@Param("tenantId") long tenantId,@Param("id") long id,@Param("amount") BigDecimal amount,@Param("currency") String currency,@Param("commandId") String commandId,@Param("operator") long operator);
}
