package com.lrj.erp.it.support;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import com.lrj.erp.kernel.context.ActionScope;
import java.util.List;
/** 在真实审计资源上验证新授权SQL片段，与旧最大范围拦截器独立，防止旧逻辑掩盖新逻辑错误。 */
@Mapper @InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface ActionScopeProbeMapper {
    List<String> records(@Param("scope") ActionScope scope);
}
