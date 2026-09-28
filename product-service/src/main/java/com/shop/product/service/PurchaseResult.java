package com.shop.product.service;

public enum PurchaseResult {
    SUCCESS("Mua hàng thành công"),
    SOLD_OUT("Sản phẩm đã hết hàng"),
    BUSY("Hệ thống đang bận, vui lòng thử lại");

    private final String message;
    PurchaseResult(String message) { this.message = message; }
    public String getMessage() { return message; }
}
