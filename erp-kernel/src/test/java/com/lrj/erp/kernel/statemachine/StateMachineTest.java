package com.lrj.erp.kernel.statemachine;

import com.lrj.erp.kernel.error.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lrj.erp.kernel.statemachine.DocumentEvent.*;
import static com.lrj.erp.kernel.statemachine.DocumentState.*;
import static org.junit.jupiter.api.Assertions.*;

/** P1 出口条件 ⑤ 的前半：非法状态迁移必须被拒绝。 */
@DisplayName("单据状态机")
class StateMachineTest {

    /** 被迁移的单据替身；守卫用它判断业务条件。 */
    record Order(boolean creditOk) { }

    private final StateMachine<Order> sm = StandardDocumentStateMachine.<Order>builder().build();

    @Test
    @DisplayName("正常路径：草稿 → 提交 → 审批 → 通过 → 处理 → 完成")
    void 正常路径() {
        Order o = new Order(true);
        DocumentState s = DRAFT;
        s = sm.fire(s, SUBMIT, o);          assertEquals(SUBMITTED, s);
        s = sm.fire(s, START_APPROVAL, o);  assertEquals(APPROVING, s);
        s = sm.fire(s, APPROVE, o);         assertEquals(APPROVED, s);
        s = sm.fire(s, PROCESS, o);         assertEquals(PROCESSING, s);
        s = sm.fire(s, FINISH, o);          assertEquals(FINISHED, s);
        assertTrue(s.terminal());
    }

    @Test
    @DisplayName("非法迁移被拒，且错误信息给出当前可用事件")
    void 非法迁移被拒() {
        DomainException ex = assertThrows(DomainException.class,
                () -> sm.fire(DRAFT, APPROVE, new Order(true)));

        assertEquals("ERP-DOC-2001", ex.errorCode().code());
        assertEquals("DRAFT", ex.details().get("from"));
        // 只说"非法"不够：调用方需要知道现在能做什么
        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) ex.details().get("allowed");
        assertEquals(List.of("CANCEL", "SUBMIT"), allowed,
                "必须给出当前可用事件，否则前端只能靠猜哪些按钮该置灰");
    }

    @Test
    @DisplayName("已产生实际业务效果后不得取消，只能关闭")
    void 处理中不得取消() {
        Order o = new Order(true);
        assertThrows(DomainException.class, () -> sm.fire(PROCESSING, CANCEL, o),
                "PROCESSING 意味着已有收发货，取消无法撤销既成事实");
        assertEquals(CLOSED, sm.fire(PROCESSING, CLOSE, o), "剩余部分应当可以关闭");
    }

    @Test
    @DisplayName("终态不接受任何事件")
    void 终态不可再迁移() {
        for (DocumentState terminal : List.of(FINISHED, CANCELLED, CLOSED)) {
            for (DocumentEvent e : DocumentEvent.values()) {
                assertThrows(DomainException.class, () -> sm.fire(terminal, e, new Order(true)),
                        "终态 %s 不应接受事件 %s".formatted(terminal, e));
            }
        }
    }

    @Test
    @DisplayName("守卫拒绝时返回 3001 而不是 2001：状态允许但业务不允许")
    void 守卫拒绝与非法迁移区分() {
        StateMachine<Order> guarded = StandardDocumentStateMachine.<Order>builder()
                .build();
        // 用一个带守卫的机器验证语义区分
        StateMachine<Order> withGuard = StateMachine.<Order>builder()
                .allow(APPROVING, APPROVE, APPROVED,
                        o -> o.creditOk() ? null : "客户信用额度不足")
                .build();

        assertEquals(APPROVED, withGuard.fire(APPROVING, APPROVE, new Order(true)));

        DomainException ex = assertThrows(DomainException.class,
                () -> withGuard.fire(APPROVING, APPROVE, new Order(false)));
        assertEquals("ERP-DOC-3001", ex.errorCode().code(),
                "守卫拒绝是业务规则不满足(422)，与非法迁移(409)是两回事");
        assertEquals("客户信用额度不足", ex.details().get("reason"));
        assertNotNull(guarded);
    }

    @Test
    @DisplayName("分批完成：PARTIAL_FINISHED 可反复接收 PARTIAL_FINISH")
    void 多次分批完成() {
        Order o = new Order(true);
        DocumentState s = sm.fire(PROCESSING, PARTIAL_FINISH, o);
        assertEquals(PARTIAL_FINISHED, s);
        s = sm.fire(s, PARTIAL_FINISH, o);
        assertEquals(PARTIAL_FINISHED, s, "多次收货应当可以反复发生");
        assertEquals(FINISHED, sm.fire(s, FINISH, o));
    }

    @Test
    @DisplayName("迁移表重复定义必须在构建期失败")
    void 重复定义立即失败() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StateMachine.<Order>builder()
                        .allow(DRAFT, SUBMIT, SUBMITTED)
                        .allow(DRAFT, SUBMIT, APPROVING)
                        .build());
        assertTrue(ex.getMessage().contains("重复定义"),
                "后写的定义静默覆盖先写的，是极难发现的一类错误");
    }
}
