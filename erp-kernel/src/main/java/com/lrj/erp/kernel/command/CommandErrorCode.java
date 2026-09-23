package com.lrj.erp.kernel.command;
import com.lrj.erp.kernel.error.ErrorCode;
/** 新HTTP命令独立错误码，兼容旧接口可选幂等头。 */
public enum CommandErrorCode implements ErrorCode {
    KEY_REQUIRED("ERP-HTTP-3001", "需要有效的UUID幂等键",400),
    KEY_CONFLICT("ERP-HTTP-2001", "该幂等键已用于不同请求",409),
    IN_PROGRESS("ERP-HTTP-2002", "请求正在处理中，请使用原幂等键重试",409);
    private final String code,message; private final int status;
    CommandErrorCode(String code,String message,int status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;} public String message(){return message;} public int httpStatus(){return status;}
}
