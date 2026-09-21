package com.lrj.erp.masterdata.controller;

import com.lrj.erp.masterdata.repository.FixtureMapper;

/**
 * 负向夹具：Controller 直接持有 Mapper —— 正是提示词禁止事项第 4 条禁止的写法。
 * 用于证明 {@code controller不得直接操作Mapper} 规则确实会拦截。测试资产，非产品代码。
 */
@SuppressWarnings("unused")
public class FixtureController {
    private FixtureMapper mapper;
}
