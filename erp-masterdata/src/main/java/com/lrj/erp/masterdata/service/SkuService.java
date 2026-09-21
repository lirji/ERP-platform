package com.lrj.erp.masterdata.service;

import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.error.SystemErrorCode;
import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.kernel.masterdata.MasterDataType;
import com.lrj.erp.masterdata.model.Sku;
import com.lrj.erp.masterdata.repository.MasterDataReferenceMapper;
import com.lrj.erp.masterdata.repository.SkuMapper;
import com.lrj.erp.masterdata.repository.UnitMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * SKU 主数据用例。实现 contracts/API_P2_MASTER_DATA.md §1 的四条共同规则。
 */
@Service
public class SkuService {

    private final SkuMapper skuMapper;
    private final UnitMapper unitMapper;
    private final MasterDataReferenceMapper referenceMapper;

    public SkuService(SkuMapper skuMapper, UnitMapper unitMapper,
                      MasterDataReferenceMapper referenceMapper) {
        this.skuMapper = skuMapper;
        this.unitMapper = unitMapper;
        this.referenceMapper = referenceMapper;
    }

    // ------------------------------------------------------------- 建档

    /**
     * 新建 SKU。
     *
     * <p>编码唯一<b>依赖数据库唯一索引</b>，不做"先查后插"：并发建档时先查后插必然重码。
     * 这里捕获 {@link DuplicateKeyException} 只是为了把它翻译成业务错误码，
     * 正确性由约束保证，不由这段代码保证。
     */
    @Transactional
    public long create(Sku sku) {
        validate(sku);
        try {
            skuMapper.insert(sku);
        } catch (DuplicateKeyException e) {
            throw new DomainException(MasterDataErrorCode.CODE_DUPLICATED,
                    Map.of("code", sku.code()));
        }
        Sku saved = skuMapper.findByCode(sku.tenantId(), sku.code());
        return saved.id();
    }

    private void validate(Sku sku) {
        if (sku.code() == null || sku.code().isBlank()) {
            throw new DomainException(MasterDataErrorCode.VALIDATION_FAILED,
                    Map.of("field", "code", "reason", "编码不能为空"));
        }
        if (sku.name() == null || sku.name().isBlank()) {
            throw new DomainException(MasterDataErrorCode.VALIDATION_FAILED,
                    Map.of("field", "name", "reason", "名称不能为空"));
        }
        // 基本单位必须存在且启用：停用的单位不能被新 SKU 引用（§1.2）
        if (!unitMapper.existsEnabled(sku.tenantId(), sku.baseUnitId())) {
            throw new DomainException(MasterDataErrorCode.DISABLED,
                    Map.of("field", "baseUnitId", "value", sku.baseUnitId(),
                           "reason", "基本单位不存在或已停用"));
        }
    }

    // ------------------------------------------------------------- 修改

    /** 修改描述字段（名称、规格、条码）。始终允许——历史单据看快照，不受影响。 */
    @Transactional
    public void updateDescriptive(Sku sku, long expectedVersion) {
        int affected = skuMapper.updateDescriptive(sku, expectedVersion);
        // 影响行数为 0 = 乐观锁冲突，绝不能当成功（开发规范 §设计 8）
        if (affected == 0) {
            throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK,
                    Map.of("entity", "md_sku", "id", sku.id()));
        }
    }

    /**
     * 修改关键字段（编码、基本单位）。
     *
     * <p>被任何单据引用后即拒绝：编码是单据上的业务标识；基本单位一改，
     * 历史库存数量的含义会整体改变——那不是"改了一个字段"，是改写了历史。
     */
    @Transactional
    public void updateKeyFields(Sku sku, long expectedVersion) {
        long tenantId = sku.tenantId();
        if (referenceMapper.isReferenced(tenantId, MasterDataType.SKU.code(), sku.id())) {
            throw new DomainException(MasterDataErrorCode.KEY_FIELD_LOCKED,
                    Map.of("mdType", "SKU", "id", sku.id(),
                           "lockedFields", List.of("code", "baseUnitId")));
        }
        try {
            int affected = skuMapper.updateKeyFields(sku, expectedVersion);
            if (affected == 0) {
                throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK,
                        Map.of("entity", "md_sku", "id", sku.id()));
            }
        } catch (DuplicateKeyException e) {
            throw new DomainException(MasterDataErrorCode.CODE_DUPLICATED,
                    Map.of("code", sku.code()));
        }
    }

    // --------------------------------------------------------- 生命周期

    /** 停用。只切断未来的引用，<b>不影响任何已有单据</b>。 */
    @Transactional
    public void disable(long tenantId, long id) {
        skuMapper.setEnabled(tenantId, id, false);
    }

    @Transactional
    public void enable(long tenantId, long id) {
        skuMapper.setEnabled(tenantId, id, true);
    }

    // ----------------------------------------------------------- 被引用

    /**
     * 单据引用一条 SKU：校验可引用 → 登记引用 → 返回<b>快照</b>。
     *
     * <p>返回快照而不是实体，是为了让调用方只能拿到"引用时的样子"。
     * 若返回实体，调用方很容易顺手持有并在之后读到被改过的值。
     *
     * @throws DomainException {@code ERP-MD-3003} 不存在 · {@code ERP-MD-3001} 已停用
     */
    @Transactional
    public MasterDataRef reference(long tenantId, long skuId,
                                   String businessType, String businessId) {
        Sku sku = skuMapper.findById(tenantId, skuId);
        if (sku == null) {
            throw new DomainException(MasterDataErrorCode.NOT_FOUND,
                    Map.of("mdType", "SKU", "id", skuId));
        }
        if (!sku.enabled()) {
            throw new DomainException(MasterDataErrorCode.DISABLED,
                    Map.of("mdType", "SKU", "code", sku.code()));
        }
        referenceMapper.register(tenantId, MasterDataType.SKU.code(), skuId,
                businessType, businessId);

        String unitName = unitMapper.findName(tenantId, sku.baseUnitId());
        return new MasterDataRef(sku.id(), sku.code(), sku.name(), unitName);
    }

    public Sku get(long tenantId, long id) {
        return skuMapper.findById(tenantId, id);
    }

    // ----------------------------------------------------------- 批量导入

    /**
     * 批量导入。<b>整批原子</b>：任一行失败则全部回滚。
     *
     * <p>部分成功会让调用方无从判断哪些进去了，重试时又重复导入前半批。
     * 因此先<b>全量校验</b>收集所有失败行再决定，而不是遇错即停——
     * 让用户改一轮就能全部修好，而不是改一行试一次。
     */
    @Transactional
    public ImportResult importBatch(long tenantId, List<Sku> rows) {
        List<ImportFailure> failures = new ArrayList<>();
        Set<String> codesInBatch = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            Sku row = rows.get(i);
            int rowNo = i + 1;
            try {
                validate(row);
            } catch (DomainException e) {
                failures.add(new ImportFailure(rowNo, row.code(), e.errorCode().message()));
                continue;
            }
            // 批内重码也要拦：数据库唯一索引会拦，但那时已无法指出是第几行
            if (!codesInBatch.add(row.code())) {
                failures.add(new ImportFailure(rowNo, row.code(), "批次内编码重复"));
                continue;
            }
            if (skuMapper.findByCode(tenantId, row.code()) != null) {
                failures.add(new ImportFailure(rowNo, row.code(), "编码已存在"));
            }
        }

        if (!failures.isEmpty()) {
            // 一行都不写：抛异常让事务回滚，同时把全部失败行带回给调用方
            throw new ImportRejectedException(rows.size(), failures);
        }

        rows.forEach(skuMapper::insert);
        return new ImportResult(rows.size(), rows.size(), List.of());
    }

    public record ImportFailure(int row, String code, String reason) { }

    public record ImportResult(int total, int imported, List<ImportFailure> failed) { }

    /** 导入被拒：携带全部失败行，供接口层组装响应。 */
    public static class ImportRejectedException extends DomainException {
        private final transient List<ImportFailure> failures;
        private final int total;

        public ImportRejectedException(int total, List<ImportFailure> failures) {
            super(MasterDataErrorCode.IMPORT_REJECTED,
                    Map.of("total", total, "failedCount", failures.size()));
            this.total = total;
            this.failures = List.copyOf(failures);
        }

        public List<ImportFailure> failures() { return failures; }

        public int total() { return total; }
    }

    /** 供接口层使用：当前上下文的租户。 */
    public static long currentTenant() {
        return AccessContextHolder.require().tenantId();
    }
}
