package io.paradaux.treasuryrestapi.model;

import lombok.Data;

/** A (subscription, account) pair the dispatcher should enqueue a delivery for. */
@Data
public class SubscriptionMatch {
    private long subscriptionId;
    private long accountId;
}
