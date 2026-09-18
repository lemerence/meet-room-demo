package com.example.meetroom.domain.exception;

/** 表示领域规则校验失败，携带可供上层映射的业务错误码。 */
public class DomainRuleViolation extends IllegalArgumentException {
    /** 稳定的领域业务错误码。 */
    private final String code;

    /** 使用业务错误码和可理解的原因构造领域异常。 */
    public DomainRuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 返回业务错误码。 */
    public String code() {
        return code;
    }
}
