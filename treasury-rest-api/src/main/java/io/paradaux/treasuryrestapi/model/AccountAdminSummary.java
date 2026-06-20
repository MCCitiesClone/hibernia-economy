package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Admin view of an account's mutable attributes (returned after an admin write). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountAdminSummary {
    private long accountId;
    private String accountType;
    private String displayName;
    private boolean archived;
    private UUID ownerUuid;
}
