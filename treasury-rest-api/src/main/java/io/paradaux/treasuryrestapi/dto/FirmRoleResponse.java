package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/**
 * A single entry in the GET /firms/me/roles list.
 * Roles are ordered by {@code rankOrder} ascending (lower = more senior).
 * {@code permissions} contains zero or more of: ADMIN, FINANCIAL, CHESTSHOP, DEFAULT.
 */
public record FirmRoleResponse(
        String name,
        int rankOrder,
        boolean proprietorLike,
        boolean defaultRole,
        List<String> permissions
) {}
