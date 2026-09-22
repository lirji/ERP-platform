package com.lrj.erp.iam.repository;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
/** 员工身份持久化；所有SQL限定租户，用户绑定须验证组织归属。 */
@Mapper
public interface EmployeeMapper {
    record Employee(long id,long tenantId,long companyId,long orgId,String employeeNo,String name,Long userId,String status,long version) {}
    Employee findCode(@Param("tenantId") long tenantId,@Param("code") String code);
    Employee lock(@Param("tenantId") long tenantId,@Param("id") long id);
    boolean validOrg(@Param("tenantId") long tenantId,@Param("companyId") long companyId,@Param("orgId") long orgId);
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    long insert(@Param("tenantId") long tenantId,@Param("companyId") long companyId,@Param("orgId") long orgId,@Param("code") String code,@Param("name") String name);
    int bind(@Param("e") Employee employee,@Param("userId") long userId);
    int leave(@Param("e") Employee employee);
    int disableUser(@Param("tenantId") long tenantId,@Param("userId") long userId);
}
