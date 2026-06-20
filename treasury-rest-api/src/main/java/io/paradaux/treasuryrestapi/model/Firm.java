package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Firm {
    private long firmId;
    private String displayName;
    private UUID proprietorUuid;
    private String discordUrl;
    private String hqRegion;
    /** Nullable — not every firm has a default account configured. */
    private Long defaultAccountId;
    private boolean archived;
    private LocalDateTime createdAt;
}
