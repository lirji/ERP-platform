package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.kernel.monitoring.IntegrityProbe.Batch;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
/** 后台跨租户对账无用户上下文；唯一方法只返回汇总计数，绝不作为业务查询入口。 */
@Mapper
public interface ReceivableIntegrityMapper {
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    Batch scan(@Param("afterId") long afterId,@Param("limit") int limit);
}
