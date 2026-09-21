package com.lrj.erp.masterdata.repository;

import com.lrj.erp.masterdata.model.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** SKU 持久层。SQL 见 resources/mapper/SkuMapper.xml。 */
@Mapper
public interface SkuMapper {

    /** 插入；违反 uk_md_sku_code 时由数据库抛出，服务层转为 ERP-MD-2001。 */
    int insert(@Param("s") Sku sku);

    Sku findById(@Param("tenantId") long tenantId, @Param("id") long id);

    Sku findByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    List<Sku> findAll(@Param("tenantId") long tenantId);

    /**
     * 更新描述字段。<b>不允许</b>在此修改 code 与 base_unit_id——
     * 关键字段的修改走 {@link #updateKeyFields}，那条路径必须先检查引用。
     *
     * @return 影响行数；0 表示乐观锁冲突（必须由调用方判定，不得当作成功）
     */
    int updateDescriptive(@Param("s") Sku sku, @Param("expectedVersion") long expectedVersion);

    /** 修改关键字段；调用方必须已确认该主数据未被引用。 */
    int updateKeyFields(@Param("s") Sku sku, @Param("expectedVersion") long expectedVersion);

    int setEnabled(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("enabled") boolean enabled);
}
