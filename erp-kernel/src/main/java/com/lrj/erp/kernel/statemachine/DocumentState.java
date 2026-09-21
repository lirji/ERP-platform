package com.lrj.erp.kernel.statemachine;

/**
 * 统一单据状态（提示词第十章）。所有核心业务单据共用这套状态集。
 *
 * <p>持久化使用 {@link #code()} 这一显式稳定值，<b>禁止依赖 ordinal</b>：
 * 用 ordinal 意味着在枚举中间插入一个状态就会静默改变所有历史单据的含义。
 */
public enum DocumentState {

    DRAFT("DRAFT", false),
    SUBMITTED("SUBMITTED", false),
    APPROVING("APPROVING", false),
    APPROVED("APPROVED", false),
    PROCESSING("PROCESSING", false),
    PARTIAL_FINISHED("PARTIAL_FINISHED", false),
    FINISHED("FINISHED", true),
    CANCELLED("CANCELLED", true),
    CLOSED("CLOSED", true);

    private final String code;
    private final boolean terminal;

    DocumentState(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }

    public String code() { return code; }

    /** 终态不再接受任何事件。 */
    public boolean terminal() { return terminal; }

    public static DocumentState of(String code) {
        for (DocumentState s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知的单据状态: " + code);
    }
}
