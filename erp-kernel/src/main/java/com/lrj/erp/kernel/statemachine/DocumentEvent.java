package com.lrj.erp.kernel.statemachine;

/** 驱动单据状态迁移的事件。与 {@link DocumentState} 同样使用显式 code。 */
public enum DocumentEvent {

    SUBMIT("SUBMIT"),
    START_APPROVAL("START_APPROVAL"),
    APPROVE("APPROVE"),
    REJECT("REJECT"),
    PROCESS("PROCESS"),
    PARTIAL_FINISH("PARTIAL_FINISH"),
    FINISH("FINISH"),
    CANCEL("CANCEL"),
    CLOSE("CLOSE");

    private final String code;

    DocumentEvent(String code) { this.code = code; }

    public String code() { return code; }
}
