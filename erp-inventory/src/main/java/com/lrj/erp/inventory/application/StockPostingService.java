package com.lrj.erp.inventory.application;

import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 库存过账：本项目最关键的用例。
 *
 * <p>三条不可妥协的不变量在这里实现：
 * <ul>
 *   <li><b>INV-01</b> 余额 == 流水代数和 —— 余额与流水在同一事务内一起写；</li>
 *   <li><b>INV-02</b> 并发扣减不超卖 —— 可用量判断写进 UPDATE 的 WHERE；</li>
 *   <li><b>INV-04</b> 同一来源行过账多次只有一次效果 —— 流水唯一索引。</li>
 * </ul>
 */
@Service
public class StockPostingService {

    /** 依赖 domain 端口而不是 MyBatis Mapper：用例编排不绑定持久化技术。 */
    private final InventoryRepository repository;

    public StockPostingService(InventoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 过账。
     *
     * @return true 表示本次真实发生了过账；false 表示同一来源行此前已过账（幂等跳过）
     * @throws DomainException {@code ERP-INV-1001} 参数非法 · {@code ERP-INV-3001} 可用量不足
     */
    @Transactional
    public boolean post(PostingRequest request) {
        validate(request);

        BigDecimal qty = request.quantity();
        BigDecimal signed = qty.multiply(BigDecimal.valueOf(request.direction().sign()));

        // 1) 先写流水。撞唯一索引即表示该来源行已过账 —— 直接返回，不碰余额。
        //    顺序很重要：先写流水再改余额，重复请求在第一步就被挡住，
        //    余额不会被重复加减。反过来则要额外的补偿逻辑。
        if (!repository.appendTransaction(request, signed, MDC.get("traceId"))) {
            return false;
        }

        // 2) 确保桶存在（并发首次过账由唯一索引收敛为一行）
        repository.ensureBucket(request.bucket());

        // 3) 改余额
        boolean applied;
        if (request.direction() == PostingDirection.IN) {
            repository.increaseOnHand(request.bucket(), qty);
            applied = true;
        } else {
            applied = repository.decreaseOnHandIfAvailable(request.bucket(), qty);
        }

        if (!applied) {
            // 出库时可用量不足。抛异常让整个事务回滚——流水也随之撤销，
            // 否则会留下一条没有对应余额变化的流水，直接破坏 INV-01。
            throw new DomainException(InventoryErrorCode.INSUFFICIENT_STOCK,
                    Map.of("skuId", request.bucket().skuId(),
                           "warehouseId", request.bucket().warehouseId(),
                           "batchNo", request.bucket().batchNo(),
                           "required", qty));
        }
        return true;
    }

    private void validate(PostingRequest r) {
        if (r.quantity() == null || r.quantity().signum() <= 0) {
            throw new DomainException(InventoryErrorCode.INVALID_POSTING,
                    Map.of("field", "quantity", "reason", "数量必须为正；方向由 direction 表达"));
        }
        if (r.sourceDocType() == null || r.sourceDocId() == null || r.sourceLineId() == null) {
            throw new DomainException(InventoryErrorCode.INVALID_POSTING,
                    Map.of("field", "source", "reason", "没有来源单据的库存变化不允许存在"));
        }
    }

    /** 当前余额；{@code available} 由余额对象算出，不读列。 */
    public InventoryBalance balance(InventoryBucket bucket) {
        return repository.findBalance(bucket);
    }

    /** INV-01 对账：该桶全部流水的代数和。 */
    public BigDecimal ledgerSum(InventoryBucket bucket) {
        return repository.sumSignedQuantity(bucket);
    }
}
