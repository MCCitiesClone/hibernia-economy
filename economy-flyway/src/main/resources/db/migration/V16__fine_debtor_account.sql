-- Generalises a government fine's debtor from a player to any account, so a
-- firm's BUSINESS account can be fined directly (PAR-48). Adds debtor_account_id
-- (the account actually debited) and makes player_uuid_bin nullable — firm fines
-- have no player. Existing player fines are backfilled to the fined player's
-- PERSONAL account so revokes keep refunding the right account.
ALTER TABLE government_fines
    ADD COLUMN debtor_account_id INT UNSIGNED NULL AFTER player_uuid_bin,
    MODIFY COLUMN player_uuid_bin BINARY(16) NULL;

-- Backfill: the debtor of each existing fine is the fined player's PERSONAL
-- account. Rows whose player no longer has a personal account stay NULL and are
-- resolved via player_uuid_bin at revoke time (legacy fallback).
UPDATE government_fines gf
  JOIN accounts a
    ON a.account_type = 'PERSONAL'
   AND a.owner_uuid_bin = gf.player_uuid_bin
   SET gf.debtor_account_id = a.account_id
 WHERE gf.debtor_account_id IS NULL;

ALTER TABLE government_fines
    ADD KEY idx_fines_debtor_account (debtor_account_id),
    ADD CONSTRAINT fk_fines_debtor_account FOREIGN KEY (debtor_account_id)
        REFERENCES accounts(account_id) ON DELETE RESTRICT;
