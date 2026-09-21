package com.lrj.erp.masterdata.model;

/**
 * SKU 主数据。
 *
 * @param code        关键字段：单据上的业务标识，被引用后不可修改
 * @param baseUnitId  关键字段：库存数量以它为准，改了会让历史库存数量的含义整体改变
 * @param name        描述字段：可修改，历史单据看到的是引用时的快照
 */
public record Sku(long id, long tenantId, long productId, long baseUnitId,
                  String code, String name, String spec, String barcode,
                  boolean batchManaged, boolean enabled, long version) { }
