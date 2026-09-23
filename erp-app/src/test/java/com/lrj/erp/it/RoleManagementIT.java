package com.lrj.erp.it;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 真实数据库及HTTP验证角色授权、并发、重放和租户边界，不以Mock证明事务。 */
@AutoConfigureMockMvc
@TestPropertySource(properties="erp.security.dev-headers.enabled=true")
class RoleManagementIT extends AbstractPostgresIT {
    static final long T=731000;
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    long adminRole;
    @BeforeEach void setup(){
        for(var table:List.of("iam_role_change_audit","erp_http_command","iam_user_role","iam_role_permission","iam_user","iam_role","iam_org"))jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO iam_tenant(id,code,name) VALUES (?,'fbl-iam-it','角色测试') ON CONFLICT DO NOTHING",T);
        long org=jdbc.queryForObject("INSERT INTO iam_org(tenant_id,org_path,code,name,org_type) VALUES (?,'/fbl/','C','公司','COMPANY') RETURNING id",Long.class,T);
        long admin=jdbc.queryForObject("INSERT INTO iam_user(tenant_id,company_id,org_id,username,display_name) VALUES (?,?,?,'admin','管理员') RETURNING id",Long.class,T,org,org);
        long reader=jdbc.queryForObject("INSERT INTO iam_user(tenant_id,company_id,org_id,username,display_name) VALUES (?,?,?,'ordinary','普通用户') RETURNING id",Long.class,T,org,org);
        adminRole=jdbc.queryForObject("INSERT INTO iam_role(tenant_id,code,name,protected) VALUES (?,'ADMIN','受保护',TRUE) RETURNING id",Long.class,T);
        long ordinary=jdbc.queryForObject("INSERT INTO iam_role(tenant_id,code,name) VALUES (?,'ORDINARY','普通') RETURNING id",Long.class,T);
        jdbc.update("INSERT INTO iam_user_role VALUES (?,?,?),(?,?,?)",T,admin,adminRole,T,reader,ordinary);
        for(long role:List.of(adminRole,ordinary))for(var permission:List.of("iam:role:read","iam:role:write"))jdbc.update("INSERT INTO iam_role_permission VALUES (?,?,?)",T,role,permission);
        jdbc.update("INSERT INTO iam_role_permission VALUES (?,?,'iam:security:admin')",T,adminRole);
    }
    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b,String user){return b.header("X-Tenant-Code","fbl-iam-it").header("X-Username",user).contentType("application/json");}
    String key(){return UUID.randomUUID().toString();}
    JsonNode createRole(String code) throws Exception {return json.readTree(mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",key()).content("{\"code\":\""+code+"\",\"name\":\"采购员\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    @Test void replayReturnsOriginalAndRejectsMismatchAndRevokedAdmin() throws Exception {
        String k=key();String body="{\"name\":\"采购员\",\"code\":\"BUYER\"}";
        String first=mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",k).content(body)).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.id").isString()).andExpect(jsonPath("$.protected").value(false)).andReturn().getResponse().getContentAsString();
        String second=mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",k).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first),json.readTree(second));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM iam_role_change_audit WHERE tenant_id=?",Integer.class,T));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM iam_role WHERE tenant_id=? AND code='BUYER'",Integer.class,T));
        mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",k).content(body.replace("BUYER","OTHER"))).andExpect(status().isConflict());
        jdbc.update("DELETE FROM iam_role_permission WHERE tenant_id=? AND permission='iam:security:admin'",T);
        mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",k).content(body)).andExpect(status().isForbidden());
    }
    @Test void ordinaryAndProtectedAndMalformedCommandsAreRejected() throws Exception {
        String body="{\"code\":\"X\",\"name\":\"X\"}";
        mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",key()).content("{broken")).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/v1/iam/roles"),"ordinary").header("Idempotency-Key",key()).content(body)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/iam/roles"),"admin").content(body)).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",key()).content("{\"code\":\"X\",\"name\":\"X\",\"tenantId\":1}")).andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/v1/iam/roles/"+adminRole),"admin").header("Idempotency-Key",key()).content("{\"expectedVersion\":\"0\",\"name\":\"X\",\"enabled\":false}")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/iam/permissions"),"admin")).andExpect(status().isOk()).andExpect(result->assertFalse(result.getResponse().getContentAsString().contains("iam:security:admin")));
        String id=createRole("NEW").get("id").asText();
        mvc.perform(as(put("/api/v1/iam/roles/"+id+"/permissions"),"admin").header("Idempotency-Key",key()).content("{\"expectedVersion\":\"0\",\"permissions\":[\"iam:security:admin\"]}")).andExpect(status().isBadRequest());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM erp_http_command WHERE tenant_id=?",Integer.class,T));
    }
    @Test void permissionsAndOptimisticVersionAreAtomic() throws Exception {
        String id=createRole("PERMS").get("id").asText();
        var payload="{\"expectedVersion\":\"0\",\"permissions\":[\"purchase:order:read\",\"purchase:order:read\"]}";
        mvc.perform(as(put("/api/v1/iam/roles/"+id+"/permissions"),"admin").header("Idempotency-Key",key()).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1")).andExpect(jsonPath("$.permissions.length()").value(1));
        mvc.perform(as(put("/api/v1/iam/roles/"+id+"/permissions"),"admin").header("Idempotency-Key",key()).content(payload.replace("purchase:order:read","purchase:order:write"))).andExpect(status().isConflict());
        assertEquals("purchase:order:read",jdbc.queryForObject("SELECT permission FROM iam_role_permission WHERE tenant_id=? AND role_id=?",String.class,T,Long.parseLong(id)));
    }
    @Test void sameKeyConcurrencyCreatesOnceAndVersionRaceHasOneWinner() throws Exception {
        String k=key();String body="{\"code\":\"RACE\",\"name\":\"并发\"}";
        try(var executor=Executors.newFixedThreadPool(4)){
            var jobs=new ArrayList<Callable<Integer>>();
            for(int i=0;i<4;i++)jobs.add(()->mvc.perform(as(post("/api/v1/iam/roles"),"admin").header("Idempotency-Key",k).content(body)).andReturn().getResponse().getStatus());
            for(var result:executor.invokeAll(jobs))assertEquals(201,result.get());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM iam_role WHERE tenant_id=? AND code='RACE'",Integer.class,T));
            long id=jdbc.queryForObject("SELECT id FROM iam_role WHERE tenant_id=? AND code='RACE'",Long.class,T);
            jobs.clear();for(int i=0;i<2;i++)jobs.add(()->mvc.perform(as(put("/api/v1/iam/roles/"+id),"admin").header("Idempotency-Key",key()).content("{\"expectedVersion\":\"0\",\"name\":\"更新\",\"enabled\":false}")).andReturn().getResponse().getStatus());
            var statuses=new ArrayList<Integer>();for(var result:executor.invokeAll(jobs))statuses.add(result.get());Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        }
    }
    @Test void listBoundsLegacyCompatibilityAndTenantIsolation() throws Exception {
        mvc.perform(as(get("/api/v1/iam/roles"),"ordinary")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber()).andExpect(jsonPath("$[0].data_scope_type").value("SELF"));
        mvc.perform(as(get("/api/v1/iam/role-directory?page=1&size=1&sort=name"),"ordinary")).andExpect(status().isOk()).andExpect(jsonPath("$.list.length()").value(1)).andExpect(jsonPath("$.total").value(2));
        for(var query:List.of("size=201","page=0","page=abc","sort=id;drop"))mvc.perform(as(get("/api/v1/iam/role-directory?"+query),"ordinary")).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/iam/roles/999999999"),"ordinary")).andExpect(status().isNotFound());
        String id=createRole("MOVE").get("id").asText();jdbc.update("UPDATE iam_role SET tenant_id=? WHERE id=?",T+1,Long.parseLong(id));
        try{mvc.perform(as(get("/api/v1/iam/roles/"+id),"ordinary")).andExpect(status().isNotFound());}finally{jdbc.update("DELETE FROM iam_role WHERE id=?",Long.parseLong(id));}
    }
}
