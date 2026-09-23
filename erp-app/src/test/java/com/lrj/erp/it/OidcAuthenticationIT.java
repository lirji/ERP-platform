package com.lrj.erp.it;

import com.lrj.erp.kernel.context.AccessContextHolder;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 RSA/JWKS + 完整过滤链 + PostgreSQL 身份映射，禁止用 Mock JWT 替代验签证明。 */
@org.springframework.boot.test.context.SpringBootTest(classes = com.lrj.erp.app.ErpApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"erp.outbox.scheduling-enabled=false", "erp.monitoring.enabled=false"})
@AutoConfigureMockMvc
@TestPropertySource(properties = {"erp.security.oidc.enabled=true", "erp.security.oidc.client-id=erp-test",
        "erp.security.oidc.public-base-url=http://localhost:8500"})
class OidcAuthenticationIT extends AbstractPostgresIT {
    private static final String ISSUER = "http://localhost:18001";
    private static final String SUBJECT = "eddd1ab0-4484-4b23-a825-654d106d949c";
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    private static volatile RSAKey published;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("erp-oidc-test-1").generate();
            published = KEY;
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(published.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            JWKS.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource
    static void oidc(DynamicPropertyRegistry registry) {
        registry.add("erp.security.oidc.issuer", () -> ISSUER);
        registry.add("erp.security.oidc.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @AfterAll static void stopJwks() { JWKS.stop(0); }
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        published = KEY;
        jdbc.update("DELETE FROM iam_user_role WHERE tenant_id=970101");
        jdbc.update("DELETE FROM iam_role_permission WHERE tenant_id=970101");
        jdbc.update("DELETE FROM iam_user WHERE tenant_id=970101");
        jdbc.update("DELETE FROM iam_role WHERE tenant_id=970101");
        jdbc.update("DELETE FROM iam_org WHERE tenant_id=970101");
        jdbc.update("DELETE FROM iam_tenant WHERE id=970101");
        jdbc.update("INSERT INTO iam_tenant(id,code,name,oidc_issuer,oidc_owner) VALUES (970101,'OIDC_IT','认证测试',?,'erp-test-org')", ISSUER);
        jdbc.update("INSERT INTO iam_org(id,tenant_id,org_path,code,name,org_type) VALUES (9701011,970101,'/9701011/','HQ','总部','COMPANY')");
        jdbc.update("INSERT INTO iam_user(id,tenant_id,company_id,org_id,username,display_name,external_id) VALUES (9701012,970101,9701011,9701011,'renamable','测试用户',?)", SUBJECT);
        jdbc.update("INSERT INTO iam_role(id,tenant_id,code,name,data_scope_type) VALUES (9701013,970101,'reader','读者','SELF')");
        jdbc.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES(970101,9701012,9701013)");
        jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission) VALUES(970101,9701013,'iam:role:read')");
    }
    private String token(Consumer<JWTClaimsSet.Builder> edit, RSAKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("erp-test").subject(SUBJECT)
                .claim("owner", "erp-test-org").issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        edit.accept(claims);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
    private String token() throws Exception { return token(c -> {}, KEY); }

    @Test void validTokenMapsStableSubjectAndIgnoresSpoofedHeaders() throws Exception {
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + token())
                .header("X-Tenant-Code", "another").header("X-Username", "admin"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(970101))
            .andExpect(jsonPath("$.userId").value(9701012));
        assertTrue(AccessContextHolder.find().isEmpty());
        jdbc.update("UPDATE iam_user SET username='changed' WHERE id=9701012");
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/iam/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings={"issuer","audience","expired","future","no-exp","no-sub","blank-sub","no-owner","owner-list","other-owner","unbound","username-as-sub"})
    void invalidIdentityIsRejected(String scenario) throws Exception {
        String value = token(c -> {
            switch(scenario) {
                case "issuer" -> c.issuer("https://untrusted.example");
                case "audience" -> c.audience("other-app");
                case "expired" -> c.expirationTime(Date.from(Instant.now().minusSeconds(120)));
                case "future" -> c.notBeforeTime(Date.from(Instant.now().plusSeconds(120)));
                case "no-exp" -> c.expirationTime(null);
                case "no-sub" -> c.subject(null);
                case "blank-sub" -> c.subject(" ");
                case "no-owner" -> c.claim("owner", null);
                case "owner-list" -> c.claim("owner", List.of("erp-test-org"));
                case "other-owner" -> c.claim("owner", "another-org");
                case "unbound" -> c.subject("not-bound");
                case "username-as-sub" -> c.subject("renamable");
                default -> throw new IllegalArgumentException();
            }
        }, KEY);
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + value))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ERP-AUTH-0002"));
        assertTrue(AccessContextHolder.find().isEmpty());
    }

    @Test void tamperedSignatureRejected() throws Exception {
        var other = new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate();
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + token(c -> {}, other)))
            .andExpect(status().isUnauthorized());
    }
    @Test void rotatedJwksKeyAccepted() throws Exception {
        var rotated = new RSAKeyGenerator(2048).keyID("erp-oidc-rotated").generate();
        published = rotated;
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + token(c -> {}, rotated)))
            .andExpect(status().isOk());
    }
    @Test void disabledUserImmediatelyRejected() throws Exception {
        String jwt = token();
        jdbc.update("UPDATE iam_user SET enabled=false WHERE id=9701012");
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + jwt))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ERP-AUTH-4004"));
    }
    @Test void disabledTenantRejected() throws Exception {
        jdbc.update("UPDATE iam_tenant SET enabled=false WHERE id=970101");
        mvc.perform(get("/api/v1/iam/me").header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ERP-AUTH-4003"));
    }
    @Test void validIdentityWithoutPermissionRejected() throws Exception {
        jdbc.update("DELETE FROM iam_role_permission WHERE tenant_id=970101");
        mvc.perform(get("/api/v1/iam/roles").header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ERP-AUTH-4001"));
    }
    @Test void largeCasdoorTokenPassesRealTomcatHeaderLimit() throws Exception {
        String jwt = token(c -> c.claim("displayName", "x".repeat(10000)), KEY);
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://127.0.0.1:" + port + "/api/v1/iam/me"))
                .header("Authorization", "Bearer " + jwt).GET().build();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    @Test void browserEntryPublicButBusinessNotPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/auth/login.html"));
        mvc.perform(get("/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.clientId").value("erp-test"));
        mvc.perform(get("/webjars/oidc-client-ts/3.2.1/dist/browser/oidc-client-ts.min.js"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/iam/me").header("X-Username", "renamable").header("X-Tenant-Code", "OIDC_IT"))
            .andExpect(status().isUnauthorized());
    }
}
