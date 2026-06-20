package io.paradaux.treasury.model.economy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a member or authorizer row on an account.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountMember {
    private int accountId;
    private UUID memberUuid;
    private UUID addedByUuid;
    private Instant createdAt;
}
