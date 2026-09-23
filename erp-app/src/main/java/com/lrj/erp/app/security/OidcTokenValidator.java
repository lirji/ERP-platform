package com.lrj.erp.app.security;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** 除标准时间与 issuer 校验外，强制受众及身份 claims，防止跨应用令牌与空身份。 */
public final class OidcTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public OidcTokenValidator(String audience) { this.audience = audience; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object owner = jwt.getClaims().get("owner");
        Object subject = jwt.getClaims().get("sub");
        if (jwt.getExpiresAt() != null && jwt.getAudience().contains(audience)
                && subject instanceof String sub && !sub.isBlank() && sub.length() <= 128
                && owner instanceof String org && !org.isBlank() && org.length() <= 128) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "无效的令牌身份或受众", null));
    }
}
