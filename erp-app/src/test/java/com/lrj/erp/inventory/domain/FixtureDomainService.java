package com.lrj.erp.inventory.domain;

import org.springframework.stereotype.Component;

/**
 * 负向夹具：领域类绑定 Spring 容器注解 —— 违反「领域层保持框架无关」。
 * 用于证明该规则确实会拦截。测试资产，非产品代码。
 */
@Component
public class FixtureDomainService {
}
