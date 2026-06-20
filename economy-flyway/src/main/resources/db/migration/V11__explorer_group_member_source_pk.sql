-- Let a player hold both a 'manual' and a 'luckperms' membership in the same
-- group (PAR-104).
--
-- V10 gave explorer_group_member a PRIMARY KEY of (group_id, player_uuid_bin),
-- which omits `source`. So if an admin manually added player P to a group and P
-- also carried the group's LuckPerms node, the reconciliation cron's
--   INSERT IGNORE ... source='luckperms'
-- collided with P's existing 'manual' row and was silently dropped. P then never
-- appeared in the cron's luckperms view, so its desired-vs-current diff never
-- converged and it re-attempted the (ignored) insert on every tick forever.
--
-- Folding `source` into the PK lets the two source rows coexist. The cron's
-- DELETE still filters on source='luckperms' (manual rows untouched), and the
-- explorer unions a player's group capabilities into a set, so a player belonging
-- to one group via two sources resolves that group's capabilities once — no
-- double-counting.
--
-- Safe on existing data: V10 rows are unique on (group_id, player_uuid_bin), so
-- adding `source` to the key cannot create duplicates.

ALTER TABLE explorer_group_member
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (group_id, player_uuid_bin, source);
