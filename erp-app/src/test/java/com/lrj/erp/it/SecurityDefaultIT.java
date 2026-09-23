package com.lrj.erp.it;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 未启用 OIDC 时不能意外生成默认账户或接受伪造开发头。 */
@AutoConfigureMockMvc
class SecurityDefaultIT extends AbstractPostgresIT {
    @Autowired MockMvc mvc;
    @Test void defaultClosed() throws Exception {
        mvc.perform(get("/api/v1/iam/me").header("X-Tenant-Code", "acme").header("X-Username", "manager"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }
}
