-- ====================================================================
-- Integration-test prelude.
--
-- The Business schema references Treasury's `accounts` table (via FK on
-- firm.default_account_id and firm_accounts.account_id) and Treasury's
-- `uuid_to_bin`/`bin_to_uuid` SQL functions. In production, Treasury's
-- schema is loaded into the same database first; in tests we run Business
-- in isolation, so this prelude defines the minimum subset Business needs
-- before applying its own schema.
-- ====================================================================

DROP FUNCTION IF EXISTS uuid_to_bin;
DROP FUNCTION IF EXISTS bin_to_uuid;

DELIMITER //
CREATE FUNCTION uuid_to_bin(_uuid CHAR(36))
    RETURNS BINARY(16) DETERMINISTIC
BEGIN
RETURN UNHEX(REPLACE(_uuid, '-', ''));
END//
CREATE FUNCTION bin_to_uuid(_bin BINARY(16))
    RETURNS CHAR(36) DETERMINISTIC
BEGIN
RETURN INSERT(INSERT(INSERT(INSERT(HEX(_bin), 9, 0, '-'), 14, 0, '-'), 19, 0, '-'), 24, 0, '-');
END//
DELIMITER ;

-- Minimal Treasury accounts stub — only the column shape Business FKs against.
DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    account_id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    display_name   VARCHAR(255),
    PRIMARY KEY (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
