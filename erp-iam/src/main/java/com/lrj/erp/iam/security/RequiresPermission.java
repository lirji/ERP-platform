package com.lrj.erp.iam.security;

import java.lang.annotation.*;

/**
 * 声明接口所需的功能权限点，如 {@code purchase:order:approve}。
 *
 * <p>判权在<b>服务端</b>执行。前端按 {@code /api/v1/iam/me} 返回的权限点置灰按钮，
 * 但那只是体验——隐藏按钮不是安全边界，绕过前端直接调用 API 必须同样被拒绝。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 所需权限点；多个之间为“全部满足”。 */
    String[] value();
}
