package com.lrj.erp.iam.service;
import com.lrj.erp.iam.repository.EmployeeMapper;
import com.lrj.erp.iam.repository.EmployeeMapper.Employee;
import com.lrj.erp.kernel.monitoring.BusinessAudit;
import com.lrj.erp.kernel.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** 员工与登录账户分离；离职撤销后续授权装配资格，不删除原角色或业务审计。 */
@Service
public class EmployeeService {
    private final EmployeeMapper mapper;
    private final BusinessAudit audit;
    public EmployeeService(EmployeeMapper mapper,BusinessAudit audit) { this.mapper=mapper;this.audit=audit; }
    /** 工号幂等；不凭不存在的公司或部门创建员工。 */
    @Transactional
    public long enroll(long tenantId,long companyId,long orgId,String code,String name,long operator) {
        require(operator>0 && code!=null && !code.isBlank() && code.length()<=64 && name!=null && !name.isBlank() && name.length()<=128,"员工字段非法");
        require(mapper.validOrg(tenantId,companyId,orgId),"公司与部门归属无效");
        Employee existing=mapper.findCode(tenantId,code);
        if(existing!=null) {
            require(existing.companyId()==companyId && existing.orgId()==orgId && existing.name().equals(name) && existing.status().equals("ACTIVE"),"工号内容冲突或员工已离职");
            return existing.id();
        }
        long id=mapper.insert(tenantId,companyId,orgId,code,name);
        audit.record(tenantId,operator,"EMPLOYEE",""+id,code,"ENROLLED");return id;
    }
    /** 同租户同组织的启用用户才可关联；数据库保证一个用户不能绑定多员工。 */
    @Transactional
    public void bindUser(long tenantId,long id,long userId,long operator) {
        require(operator>0,"操作人非法");Employee e=required(tenantId,id);
        require(e.status().equals("ACTIVE"),"离职员工不可绑定用户");
        if(e.userId()!=null && e.userId()==userId) return;
        require(mapper.bind(e,userId)==1,"用户归属不匹配或已经关联其他账户");
        audit.record(tenantId,operator,"EMPLOYEE",""+id,e.employeeNo(),"USER_BOUND");
    }
    /** ACTIVE→LEFT 并禁用关联用户同事务；已有在途请求不宣称自动撤销。 */
    @Transactional
    public void leave(long tenantId,long id,long operator) {
        require(operator>0,"操作人非法");Employee e=required(tenantId,id);
        if(e.status().equals("LEFT")) return;
        require(mapper.leave(e)==1,"员工状态并发修改");
        if(e.userId()!=null) require(mapper.disableUser(tenantId,e.userId())==1,"关联用户不存在");
        audit.record(tenantId,operator,"EMPLOYEE",""+id,e.employeeNo(),"LEFT");
    }
    private Employee required(long tenantId,long id) { Employee e=mapper.lock(tenantId,id);require(e!=null,"员工不存在");return e; }
    private void require(boolean ok,String reason) { if(!ok) throw new DomainException(SystemErrorCode.MALFORMED_BODY,Map.of("reason",reason)); }
}
