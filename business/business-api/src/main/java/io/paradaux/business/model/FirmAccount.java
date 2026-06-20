package io.paradaux.business.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirmAccount {
    private Integer firmId;
    private Integer accountId;
    private LocalDateTime addedAt;
}
