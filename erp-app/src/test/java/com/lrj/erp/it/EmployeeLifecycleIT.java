package com.lrj.erp.it;
import com.lrj.erp.iam.service.*;
import com.lrj.erp.kernel.error.DomainException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 员工链路缺口：验证离职禁用用户与跨租户关联拒绝。 */
class EmployeeLifecycleIT extends AbstractPostgresIT {
    static final long T=291;
    @Autowired EmployeeService employees;
    @Autowired AccessContextAssembler access;
    @Autowired JdbcTemplate jdbc;
    long company,dept,user;
    @BeforeEach void setup() {
        for(String table:List.of("iam_employee","iam_user","iam_org")) jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO iam_tenant(id,code,name) VALUES (?,'employee-it','测试租户') ON CONFLICT DO NOTHING",T);
        company=jdbc.queryForObject("INSERT INTO iam_org(tenant_id,code,name,org_type,org_path) VALUES (?,'COMP','公司','COMPANY','/c/') RETURNING id",Long.class,T);
        dept=jdbc.queryForObject("INSERT INTO iam_org(tenant_id,parent_id,code,name,org_type,org_path) VALUES (?,?,'DEPT','部门','DEPT','/c/d/') RETURNING id",Long.class,T,company);
        user=jdbc.queryForObject("INSERT INTO iam_user(tenant_id,company_id,org_id,username,display_name) VALUES (?,?,?,'employee-user','用户') RETURNING id",Long.class,T,company,dept);
    }
    @Test void leaveDisablesFutureAuthorizationAndIsIdempotent() {
        long id=employees.enroll(T,company,dept,"E1","演示员工",user);
        employees.bindUser(T,id,user,user);assertNotNull(access.assemble("employee-it","employee-user","trace"));
        employees.leave(T,id,user);employees.leave(T,id,user);
        assertThrows(DomainException.class,()->access.assemble("employee-it","employee-user","trace"));
        assertThrows(DomainException.class,()->employees.bindUser(T,id,user,user));
        assertThrows(DomainException.class,()->employees.enroll(T,company,dept,"E1","演示员工",user));
    }
    @Test void wrongTenantOrOrganizationCannotBind() {
        long id=employees.enroll(T,company,dept,"E2","演示员工",user);
        assertThrows(DomainException.class,()->employees.bindUser(T+1,id,user,user));
        jdbc.update("UPDATE iam_user SET company_id=? WHERE tenant_id=? AND id=?",company+1,T,user);
        assertThrows(DomainException.class,()->employees.bindUser(T,id,user,user));
    }
}
