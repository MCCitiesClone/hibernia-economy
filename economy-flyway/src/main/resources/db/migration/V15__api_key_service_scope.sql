-- Adds a SERVICE key scope to api_keys for trusted backend callers (e.g. the
-- economy-explorer admin firm tool) that operate across firms/accounts rather
-- than as a single player or firm. SERVICE tokens carry no `acc`/`firm` JWT
-- claim; treasury-rest-api gates its admin endpoints on this scope. Additive
-- enum change; existing rows keep their value.
ALTER TABLE api_keys
    MODIFY COLUMN key_type ENUM('PERSONAL','BUSINESS','GOVERNMENT','SERVICE') NOT NULL DEFAULT 'PERSONAL';
