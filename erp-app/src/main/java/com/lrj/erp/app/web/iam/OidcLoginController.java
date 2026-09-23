package com.lrj.erp.app.web.iam;

import com.lrj.erp.app.security.OidcProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 最小登录入口与公开配置，不承载 ERP 业务 UI，也不向浏览器暴露 client secret。 */
@Controller
public class OidcLoginController {
    private final OidcProperties properties;
    public OidcLoginController(OidcProperties properties) { this.properties = properties; }

    /** 授权码回调与入口复用同页，由 oidc-client-ts 校验 state/PKCE 并清理 URL。 */
    @GetMapping({"/login", "/auth/callback"})
    public String login() { return "forward:/auth/login.html"; }

    /** 回调来自受控配置，不接受 Host、query 或门户传入的 client/issuer 覆盖。 */
    @GetMapping("/auth/config")
    public ResponseEntity<LoginConfiguration> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LoginConfiguration(
                properties.enabled(), properties.issuer(), properties.clientId(), properties.publicBaseUrl()));
    }

    /** 浏览器可公开读取的 OIDC 参数，不含凭据。 */
    public record LoginConfiguration(boolean enabled, String issuer, String clientId, String publicBaseUrl) { }
}
