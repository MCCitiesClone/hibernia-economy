package io.paradaux.business.model;

public enum RolePermission {

    ADMIN,
    FINANCIAL,
    CHESTSHOP,
    DEFAULT;

    public static RolePermission fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Permission value cannot be null or blank");
        }

        String normalized = value.trim().toUpperCase();

        // Alias handling for backward compatibility
        if ("CHEST_SHOP".equals(normalized)) {
            return CHESTSHOP;
        }
        if ("ADMINISTRATOR".equals(normalized)) {
            return ADMIN;
        }

        // Match enum names
        for (RolePermission permission : values()) {
            if (permission.name().equalsIgnoreCase(normalized)) {
                return permission;
            }
        }

        throw new IllegalArgumentException("No permission matched value: " + value);
    }
}