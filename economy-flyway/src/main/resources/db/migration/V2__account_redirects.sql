-- =====================================================================
-- account_redirects: route Vault calls for legacy "player" UUIDs onto
-- their canonical GOVERNMENT accounts.
--
-- DemocracyCraft's previous economy ran on EssentialsX where government
-- ledgers (DCGovernment, GovReserve, GovEducation, …) were stored as
-- regular Essentials "players" with their own UUIDs. Plugins called
-- Vault with the player UUID/name; the money landed in the player
-- account. Treasury models those as actual GOVERNMENT accounts owned
-- by the virtual treasury sentinel — but the Vault clients still know
-- the legacy player UUIDs.
--
-- This table maps a legacy player UUID to the GOVERNMENT account_id
-- that should receive their Vault calls. Read by Treasury's
-- VaultEconomyAdapter / LedgerService before the resolveOrCreatePersonal
-- fallback. Written by TreasuryIngest during the EssentialsX migration
-- (see ingest.essentialsx.government-name-allowlist).
--
-- Native TreasuryApi callers are unaffected — they target accounts by
-- ID and never go through the redirect.
-- =====================================================================
CREATE TABLE account_redirects (
    redirect_uuid_bin BINARY(16)   NOT NULL,
    account_id        INT UNSIGNED NOT NULL,
    note              VARCHAR(255) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (redirect_uuid_bin),
    KEY idx_redirects_account (account_id),
    CONSTRAINT fk_redirect_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
