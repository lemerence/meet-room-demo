package com.example.meetroom.domain.model;

/** 表示取消操作结果，区分本次取消成功与此前已经取消。 */
public enum CancellationResult {
    /** 本次调用成功将有效预约变更为已取消。 */
    CANCELLED("取消成功"),
    /** 预约此前已取消，本次调用不再修改状态和审计时间。 */
    ALREADY_CANCELLED("预约已经取消无需重复取消");

    /** 取消操作结果的提示文字。 */
    private final String message;

    /** 为取消结果设置对外提示。 */
    CancellationResult(String message) {
        this.message = message;
    }

    /** 返回操作结果提示。 */
    public String message() {
        return message;
    }
}
