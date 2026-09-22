package com.lrj.erp.sales.infrastructure;
import com.lrj.erp.sales.domain.SalesReturnRepository.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
/** 原来源行 FOR UPDATE 保护累计退货上界及金额尾差。 */
@Mapper
public interface SalesReturnMapper {
    Source lockSource(@Param("tenantId") long tenantId,@Param("lineId") long lineId);
    Return lock(@Param("tenantId") long tenantId,@Param("id") long id);
    Return command(@Param("tenantId") long tenantId,@Param("commandId") String commandId);
    /** INSERT RETURNING 显式绑定租户，查询仍保留租户及权限拦截。 */
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    long insert(@Param("r") Return document);
    int addReturned(@Param("tenantId") long tenantId,@Param("sourceLineId") long sourceLineId,@Param("quantity") BigDecimal quantity);
    int transition(@Param("r") Return document,@Param("next") String next);
    int amount(@Param("r") Return document,@Param("amount") BigDecimal amount);
}
