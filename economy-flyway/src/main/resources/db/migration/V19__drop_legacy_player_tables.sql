-- Completes the player-directory consolidation begun in V17 (PAR-35). The unified
-- `economy_players` table is now the single source of truth for UUID ↔ current name
-- and last-login epoch, and every consumer has been cut over:
--   * Business           — reads economy_players; no longer writes firm_players.
--   * treasury-rest-api  — name/UUID resolution joins economy_players.
--   * economy-explorer   — name joins + active-player health read economy_players.
--   * Treasury           — PlayerLoginListener is the sole writer; balance tax reads
--                          economy_players.last_login_epoch (player_login_times gone).
--
-- With no remaining readers or writers — and no foreign keys referencing either
-- table — the two legacy stores can be dropped. V17 backfilled economy_players from
-- both, so no data is lost. IF EXISTS keeps this idempotent across environments that
-- may have already been cleaned up manually.
DROP TABLE IF EXISTS firm_players;
DROP TABLE IF EXISTS player_login_times;
