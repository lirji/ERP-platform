package com.lrj.erp.app.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

/** 认证配置随部署固定；启用时缺少安全边界即拒绝启动。 */
@ConfigurationProperties("erp.security.oidc")
public record OidcProperties(boolean enabled, String issuer, String jwkSetUri, String clientId,
                             String publicBaseUrl) {
    /** 只允许 HTTPS 或明确的本机开发 HTTP；不从请求 Host 推导回调，避免污染。 */
    public void validate() {
        if (!enabled) return;
        checkUri(issuer, "issuer");
        checkUri(jwkSetUri, "jwk-set-uri");
        checkUri(publicBaseUrl, "public-base-url");
        if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("OIDC client-id 必填");
        URI base = URI.create(publicBaseUrl);
        if (!(base.getPath().isEmpty()) || publicBaseUrl.endsWith("/"))
            throw new IllegalArgumentException("OIDC public-base-url 只能是 origin，不能包含路径或尾斜杠");
    }

    private static void checkUri(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("OIDC " + name + " 必填");
        URI uri = URI.create(value);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                || "host.docker.internal".equals(uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getQuery() != null || !("https".equals(uri.getScheme())
                || (local && "http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("OIDC " + name + " 必须是 HTTPS 或本机开发地址");
    }
}
