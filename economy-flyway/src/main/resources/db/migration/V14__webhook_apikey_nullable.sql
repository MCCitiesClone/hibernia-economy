-- Allow webhook subscriptions that aren't tied to an in-game API key.
--
-- V13 scoped every webhook to an api_keys.key_id (the JWT key that created it).
-- economy-explorer manages webhooks for players who sign in via Keycloak and
-- have no API key, so those rows are owner-scoped (owner_uuid_bin) with no key.
-- Make api_key_id nullable: NULL = explorer-managed (owner-scoped), non-NULL =
-- created via a REST API key. The FK stays and simply isn't enforced on NULL.
-- The dispatcher matches by account/firm scope, not by api_key_id, so delivery
-- is unaffected either way.
ALTER TABLE webhook_subscription
    MODIFY api_key_id INT UNSIGNED NULL;
