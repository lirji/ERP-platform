package com.lrj.erp.masterdata.repository;

/** 负向夹具：扮演一个 Mapper，供 Controller→Mapper 规则的负向证明使用。测试资产，非产品代码。 */
public interface FixtureMapper {
    String selectSomething();
}
