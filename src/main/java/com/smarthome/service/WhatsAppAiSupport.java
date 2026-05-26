package com.smarthome.service;

import com.smarthome.entity.Product;

public final class WhatsAppAiSupport {

    private WhatsAppAiSupport() {}

    public static Product.UnitType safeUnit(String s) {
        if (s == null || s.isBlank()) return Product.UnitType.UNIT;
        try {
            return Product.UnitType.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return Product.UnitType.UNIT;
        }
    }
}
