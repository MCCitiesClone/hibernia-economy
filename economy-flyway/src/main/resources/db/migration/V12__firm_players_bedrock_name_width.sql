-- Widen the firm_players name cache so Bedrock/Floodgate names fit.
--
-- Floodgate prefixes Bedrock IGNs with '.' (and they can run up to 16 chars),
-- so a name like '.SomeLongGamertag' exceeds the original VARCHAR(16) and was
-- silently truncated on store — or collided two distinct players onto one
-- name_lower and failed the UNIQUE upsert, leaving them uncached. That cache is
-- the canonical name<->uuid map the REST API (findPlayerUuidByName), the Business
-- plugin's firm-name resolver, and the explorer's name display all read, so a
-- truncated/missing row shows a bare UUID or fails a by-name lookup.
--
-- Widen current_name + the generated name_lower to VARCHAR(32) (matching
-- explorer_identity.minecraft_name). Drop and re-add the UNIQUE index around the
-- generated-column change so it rebuilds cleanly. Widening is safe on existing
-- data (no truncation of values already stored).

ALTER TABLE firm_players DROP INDEX uq_firm_players_name;

ALTER TABLE firm_players
    MODIFY current_name VARCHAR(32) NOT NULL,
    MODIFY name_lower   VARCHAR(32) AS (LOWER(current_name)) VIRTUAL;

ALTER TABLE firm_players ADD CONSTRAINT uq_firm_players_name UNIQUE (name_lower);
