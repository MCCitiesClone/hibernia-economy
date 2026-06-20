package io.paradaux.treasuryrestapi.dto;

/**
 * The JSON body POSTed to a subscriber. {@code accountId} is the subscribed
 * account the transaction matched (one of the firm's accounts for BUSINESS
 * subscriptions); {@code transaction} is that account's posting view, identical
 * in shape to the pull-feed items. The {@code X-Treasury-Signature} header is
 * {@code sha256=<hmac>} over the raw body using the subscription secret.
 */
public record WebhookEvent(String event,
                           long deliveryId,
                           long subscriptionId,
                           long accountId,
                           TransactionItem transaction) {}
