package com.lrj.erp.it;
import com.fasterxml.jackson.databind.*;
import com.lrj.erp.iam.service.*;
import com.lrj.erp.it.support.*;
import com.lrj.erp.kernel.context.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 角色主体范围与真实SQL授权矩阵；包含相邻前缀、跨租户和异权限不交叉扩大。 */
@AutoConfigureMockMvc @TestPropertySource(properties="erp.security.dev-headers.enabled=true")
class RoleScopeIT extends AbstractPostgresIT {
    static final long T=732000;
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired AccessContextAssembler identities;@Autowired ActionScopeService scopes;
    @Autowired ActionScopeProbeMapper probe;@Autowired SqlCaptor sqlCaptor;
    long orgA,orgB,dept,user,roleA,roleB;
    @BeforeEach void seed(){
        AccessContextHolder.clear();
        for(var table:List.of("iam_role_scope_org","iam_role_scope_company","iam_role_change_audit","erp_http_command","iam_user_role","iam_role_permission","iam_user","iam_role","iam_org"))jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("DELETE FROM doc_audit_log WHERE tenant_id IN (?,?)",T,T+1);
        jdbc.update("INSERT INTO iam_tenant(id,code,name) VALUES (?,'scope-it','范围测试') ON CONFLICT DO NOTHING",T);
        orgA=org("A","/1/23/","COMPANY",null);orgB=org("B","/1/234/","COMPANY",null);dept=org("D","/1/23/7/","DEPT",orgA);
        user=jdbc.queryForObject("INSERT INTO iam_user(tenant_id,company_id,org_id,username,display_name) VALUES (?,?,?,'admin','范围测试员') RETURNING id",Long.class,T,orgA,dept);
        long admin=role("ADMIN","SELF",true);
        for(var p:List.of("iam:role:read","iam:role:write","iam:security:admin","iam:org:read"))permission(admin,p);
        roleA=role("A","SPECIFIED_COMPANY",false);roleB=role("B","SPECIFIED_COMPANY",false);
        permission(roleA,"purchase:order:write");permission(roleA,"purchase:order:read");permission(roleB,"purchase:order:read");
        jdbc.update("INSERT INTO iam_role_scope_company VALUES (?,?,?),(?,?,?)",T,roleA,orgA,T,roleB,orgB);
        audit(T,orgA,"/1/23/","A");audit(T,orgA,"/1/23/7/","A-child");audit(T,orgB,"/1/234/","B");audit(T+1,orgA,"/1/23/","foreign");
    }
    @AfterEach void clear(){AccessContextHolder.clear();}
    long org(String code,String path,String type,Long parent){return jdbc.queryForObject("INSERT INTO iam_org(tenant_id,code,name,org_path,org_type,parent_id) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,T,code,code,path,type,parent);}
    long role(String code,String scope,boolean protectedRole){long id=jdbc.queryForObject("INSERT INTO iam_role(tenant_id,code,name,data_scope_type,protected) VALUES (?,?,?,?,?) RETURNING id",Long.class,T,code,code,scope,protectedRole);jdbc.update("INSERT INTO iam_user_role VALUES (?,?,?)",T,user,id);return id;}
    void permission(long id,String p){jdbc.update("INSERT INTO iam_role_permission VALUES (?,?,?)",T,id,p);}
    void audit(long tenant,long company,String path,String business){jdbc.update("INSERT INTO doc_audit_log(tenant_id,user_id,business_type,business_id,action,company_id,org_path,created_by) VALUES (?,?,'SCOPE',?,'CREATE',?,?,?)",tenant,user,business,company,path,user);}
    void login(){AccessContextHolder.set(identities.assemble("scope-it","admin","scope-test"));}
    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b){return b.header("X-Tenant-Code","scope-it").header("X-Username","admin").contentType("application/json");}
    String body(String type,List<String> orgs,List<String> companies) throws Exception{return json.writeValueAsString(Map.of("expectedVersion","0","scope",Map.of("type",type,"orgIds",orgs,"companyIds",companies,"warehouseMode","NONE","warehouseIds",List.of())));}
    @Test void permissionSpecificUnionAndTenantPredicateAreExecutedInSql(){
        login();sqlCaptor.clear();
        assertEquals(List.of("A","A-child","B"),probe.records(scopes.forPermission("purchase:order:read")));
        assertEquals(List.of("A","A-child"),probe.records(scopes.forPermission("purchase:order:write")));
        String sql=sqlCaptor.lastSqlContaining("doc_audit_log");assertNotNull(sql);assertTrue(sql.contains("tenant_id"));assertTrue(sql.contains("company_id"));
        assertTrue(probe.records(scopes.forPermission("finance:payable:read")).isEmpty());
        // 旧上下文不能把指定集合降级成部门及下级放行。
        assertTrue(AccessContextHolder.require().dataScope().denied());
    }
    @Test void missingDisabledAndPrefixScopesNeverFallBack(){
        jdbc.update("DELETE FROM iam_role_scope_company WHERE tenant_id=? AND role_id=?",T,roleA);
        login();assertTrue(probe.records(scopes.forPermission("purchase:order:write")).isEmpty());
        jdbc.update("UPDATE iam_role SET data_scope_type='SPECIFIED_ORG' WHERE id=?",roleA);
        jdbc.update("INSERT INTO iam_role_scope_org VALUES (?,?,?)",T,roleA,orgA);
        assertEquals(List.of("A","A-child"),probe.records(scopes.forPermission("purchase:order:write")));
        jdbc.update("UPDATE iam_role SET enabled=FALSE WHERE id=?",roleA);
        assertTrue(probe.records(scopes.forPermission("purchase:order:write")).isEmpty());
    }
    @Test void scopeUpdateEchoesExactIdsAndRejectsInvalidLists() throws Exception {
        String k=UUID.randomUUID().toString(),payload=body("SPECIFIED_ORG",List.of(Long.toString(orgA),Long.toString(orgB)),List.of());
        mvc.perform(as(put("/api/v1/iam/roles/"+roleA+"/data-scope")).header("Idempotency-Key",k).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.scope.orgIds.length()").value(2)).andExpect(jsonPath("$.scope.companyIds.length()").value(0)).andExpect(jsonPath("$.version").value("1"));
        mvc.perform(as(put("/api/v1/iam/roles/"+roleA+"/data-scope")).header("Idempotency-Key",k).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1"));
        mvc.perform(as(get("/api/v1/iam/roles/"+roleA))).andExpect(status().isOk()).andExpect(jsonPath("$.scope.type").value("SPECIFIED_ORG")).andExpect(jsonPath("$.scopeState").value("VALID"));
        for(String invalid:List.of(body("SPECIFIED_COMPANY",List.of(),List.of()),body("SPECIFIED_COMPANY",List.of(),List.of(Long.toString(dept))),body("SPECIFIED_ORG",List.of("999999999"),List.of()),body("SELF",List.of(Long.toString(orgA)),List.of())))
            mvc.perform(as(put("/api/v1/iam/roles/"+roleB+"/data-scope")).header("Idempotency-Key",UUID.randomUUID().toString()).content(invalid)).andExpect(status().isBadRequest());
    }
    @Test void historicalUnconfiguredAndBoundedOrganizationTree() throws Exception {
        jdbc.update("DELETE FROM iam_role_scope_company WHERE tenant_id=? AND role_id=?",T,roleB);
        mvc.perform(as(get("/api/v1/iam/roles/"+roleB))).andExpect(status().isOk()).andExpect(jsonPath("$.scopeState").value("UNCONFIGURED")).andExpect(jsonPath("$.scope").isEmpty());
        mvc.perform(as(get("/api/v1/iam/orgs/tree"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        jdbc.update("INSERT INTO iam_org(tenant_id,code,name,org_path) SELECT ?, 'BOUND-'||i,'节点','/limit/'||i||'/' FROM generate_series(1,2001) i",T);
        mvc.perform(as(get("/api/v1/iam/orgs/tree"))).andExpect(status().isUnprocessableEntity());
    }
}
