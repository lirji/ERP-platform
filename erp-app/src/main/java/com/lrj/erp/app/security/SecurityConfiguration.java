package com.lrj.erp.app.security;

import com.lrj.erp.app.web.FilterErrorWriter;
import com.lrj.erp.iam.security.AuthErrorCode;
import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.kernel.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import java.time.Duration;

/** OIDC 验签 → 本地身份映射 → 既有 RBAC；无认证配置时受保护入口保持拒绝。 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
public class SecurityConfiguration {
    /** 禁用 Servlet 自动注册，避免上下文过滤器先于验签执行或重复执行。 */
    @Bean
    FilterRegistrationBean<AccessContextFilter> accessContextRegistration(AccessContextFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** 登录回调是匿名静态入口；业务路径只有成功装配本地身份后才可进入。 */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, OidcProperties props,
            AccessContextFilter contextFilter, FilterErrorWriter errors,
            @Value("${erp.security.dev-headers.enabled:false}") boolean devHeaders) throws Exception {
        props.validate();
        if (props.enabled() && devHeaders)
            throw new IllegalArgumentException("OIDC 与开发请求头身份不能同时启用");
        http.csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                    "/actuator/metrics", "/actuator/metrics/**", "/login", "/auth/callback",
                    "/auth/config", "/auth/login.html", "/auth/login.js", "/auth/login.css",
                    "/webjars/oidc-client-ts/3.2.1/dist/browser/oidc-client-ts.min.js").permitAll()
                .anyRequest().access((authentication, context) ->
                    new AuthorizationDecision(AccessContextHolder.find().isPresent())))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> errors.write(res,
                    new DomainException(AuthErrorCode.NO_CREDENTIAL)))
                .accessDeniedHandler((req, res, ex) -> errors.write(res,
                    new DomainException(AuthErrorCode.PERMISSION_DENIED))))
            .addFilterAfter(contextFilter, BearerTokenAuthenticationFilter.class);
        if (props.enabled()) {
            // JWKS 客户端缓存/轮换密钥，显式超时避免认证依赖耗尽请求线程。
            var rest = new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(2))
                    .setReadTimeout(Duration.ofSeconds(3)).build();
            var decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).restOperations(rest).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(props.issuer()), new OidcTokenValidator(props.clientId())));
            http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder))
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setHeader("WWW-Authenticate", "Bearer");
                    errors.write(res, new DomainException(AuthErrorCode.INVALID_CREDENTIAL));
                }));
        }
        return http.build();
    }
}
