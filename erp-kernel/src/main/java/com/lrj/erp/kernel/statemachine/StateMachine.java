package com.lrj.erp.kernel.statemachine;

import com.lrj.erp.kernel.error.DomainException;

import java.util.*;

/**
 * 单据状态机。迁移由 {@code (CurrentState, Event) -> NextState} 表加守卫定义。
 *
 * <p>存在的意义是让状态判断<b>只有一个入口</b>。提示词禁止事项第 7 条禁止
 * 用 {@code if (status == 1)} 散落各处控制业务——那样每加一个状态都要找遍全仓库，
 * 且没有任何机制保证找全。
 *
 * <p>本类不可变，可安全共享。
 *
 * @param <T> 被迁移的单据类型
 */
public final class StateMachine<T> {

    /** (from, event) -> (to, guard) */
    private final Map<DocumentState, Map<DocumentEvent, Transition<T>>> table;

    private StateMachine(Map<DocumentState, Map<DocumentEvent, Transition<T>>> table) {
        this.table = table;
    }

    private record Transition<T>(DocumentState to, TransitionGuard<T> guard) { }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 触发一次迁移，返回目标状态。
     *
     * @throws DomainException {@code ERP-DOC-2001} 非法迁移（details 含当前可用事件）
     *                         {@code ERP-DOC-3001} 守卫拒绝
     */
    public DocumentState fire(DocumentState current, DocumentEvent event, T subject) {
        if (current.terminal()) {
            throw new DomainException(DocumentErrorCode.ILLEGAL_TRANSITION,
                    Map.of("from", current.code(), "event", event.code(),
                           "allowed", List.of(), "reason", "单据已处于终态"));
        }

        Transition<T> transition = table.getOrDefault(current, Map.of()).get(event);
        if (transition == null) {
            // details 必须给出当前可用事件：只说"非法迁移"会让调用方（尤其是前端
            // 按钮置灰逻辑）只能靠猜。见 contracts/ERROR_CODES.md 的说明。
            throw new DomainException(DocumentErrorCode.ILLEGAL_TRANSITION,
                    Map.of("from", current.code(),
                           "event", event.code(),
                           "allowed", allowedEvents(current)));
        }

        String rejection = transition.guard().reject(subject);
        if (rejection != null) {
            throw new DomainException(DocumentErrorCode.GUARD_FAILED,
                    Map.of("from", current.code(), "event", event.code(), "reason", rejection));
        }
        return transition.to();
    }

    /** 当前状态下允许的事件，供前端置灰与错误提示使用。 */
    public List<String> allowedEvents(DocumentState current) {
        return table.getOrDefault(current, Map.of()).keySet().stream()
                .map(DocumentEvent::code)
                .sorted()
                .toList();
    }

    public boolean canFire(DocumentState current, DocumentEvent event) {
        return !current.terminal() && table.getOrDefault(current, Map.of()).containsKey(event);
    }

    public static final class Builder<T> {

        private final Map<DocumentState, Map<DocumentEvent, Transition<T>>> table =
                new EnumMap<>(DocumentState.class);

        public Builder<T> allow(DocumentState from, DocumentEvent event, DocumentState to) {
            return allow(from, event, to, TransitionGuard.alwaysAllow());
        }

        public Builder<T> allow(DocumentState from, DocumentEvent event, DocumentState to,
                                TransitionGuard<T> guard) {
            Map<DocumentEvent, Transition<T>> row =
                    table.computeIfAbsent(from, k -> new EnumMap<>(DocumentEvent.class));
            if (row.containsKey(event)) {
                // 重复定义几乎总是复制粘贴错误，且后写的会静默覆盖先写的
                throw new IllegalStateException(
                        "迁移重复定义: %s --%s-->".formatted(from.code(), event.code()));
            }
            row.put(event, new Transition<>(to, guard));
            return this;
        }

        public StateMachine<T> build() {
            Map<DocumentState, Map<DocumentEvent, Transition<T>>> copy =
                    new EnumMap<>(DocumentState.class);
            table.forEach((k, v) -> copy.put(k, Collections.unmodifiableMap(new EnumMap<>(v))));
            return new StateMachine<>(Collections.unmodifiableMap(copy));
        }
    }
}
