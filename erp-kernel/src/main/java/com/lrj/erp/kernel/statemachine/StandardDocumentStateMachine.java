package com.lrj.erp.kernel.statemachine;

import static com.lrj.erp.kernel.statemachine.DocumentEvent.*;
import static com.lrj.erp.kernel.statemachine.DocumentState.*;

/**
 * 标准单据流转表。采购订单、销售订单等共用这套骨架，各自通过守卫补业务条件。
 *
 * <pre>
 * DRAFT ──SUBMIT──→ SUBMITTED ──START_APPROVAL──→ APPROVING ──APPROVE──→ APPROVED
 *                                                     │                     │
 *                                                  REJECT                PROCESS
 *                                                     ↓                     ↓
 *                                                   DRAFT              PROCESSING ──PARTIAL_FINISH──→ PARTIAL_FINISHED
 *                                                                           │                              │
 *                                                                        FINISH ←───────FINISH─────────────┘
 *                                                                           ↓
 *                                                                       FINISHED
 * </pre>
 */
public final class StandardDocumentStateMachine {

    private StandardDocumentStateMachine() { }

    public static <T> StateMachine.Builder<T> builder() {
        return StateMachine.<T>builder()
                .allow(DRAFT,            SUBMIT,         SUBMITTED)
                .allow(DRAFT,            CANCEL,         CANCELLED)
                .allow(SUBMITTED,        START_APPROVAL, APPROVING)
                .allow(SUBMITTED,        CANCEL,         CANCELLED)
                .allow(APPROVING,        APPROVE,        APPROVED)
                // 驳回回到 DRAFT 而不是 SUBMITTED：驳回意味着要改内容，
                // 回到 SUBMITTED 会让单据停在一个"已提交但需修改"的矛盾状态
                .allow(APPROVING,        REJECT,         DRAFT)
                .allow(APPROVING,        CANCEL,         CANCELLED)
                .allow(APPROVED,         PROCESS,        PROCESSING)
                .allow(APPROVED,         CANCEL,         CANCELLED)
                .allow(PROCESSING,       PARTIAL_FINISH, PARTIAL_FINISHED)
                .allow(PROCESSING,       FINISH,         FINISHED)
                // PROCESSING 之后不允许 CANCEL：已经产生了收发货等实际业务效果，
                // 取消无法撤销既成事实，只能"关闭"剩余部分（P4 出口条件 ④）
                .allow(PROCESSING,       CLOSE,          CLOSED)
                // 多次收货/发货：PARTIAL_FINISHED 可以反复接收 PARTIAL_FINISH
                .allow(PARTIAL_FINISHED, PARTIAL_FINISH, PARTIAL_FINISHED)
                .allow(PARTIAL_FINISHED, FINISH,         FINISHED)
                .allow(PARTIAL_FINISHED, CLOSE,          CLOSED);
    }
}
