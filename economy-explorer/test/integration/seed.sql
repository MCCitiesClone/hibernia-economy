-- Deterministic fixtures for the integration suite. Designed so each assertion
-- has an unambiguous expected value. UUIDs are 32-hex; personal accounts store
-- their display_name as the hyphenated UUID (the real-world "junk name" case)
-- so name resolution via firm_players is actually exercised. DAVE is absent
-- from firm_players to test the short-UUID fallback.

-- Players (uuid -> name) — everyone except DAVE.
INSERT INTO firm_players (player_uuid_bin, current_name) VALUES
  (UNHEX('0000000000000000000000000000A1CE'), 'Alice'),
  (UNHEX('00000000000000000000000000000B0B'), 'Bob'),
  (UNHEX('0000000000000000000000000000CA01'), 'Carol');

-- Accounts. 1/2/7 personal (active), 6 personal (archived), 3 business, 4 SYSTEM, 5 government.
INSERT INTO accounts (account_id, account_type, owner_uuid_bin, display_name, is_archived) VALUES
  (1, 'PERSONAL',   UNHEX('0000000000000000000000000000A1CE'), '00000000-0000-0000-0000-00000000a1ce', 0),
  (2, 'PERSONAL',   UNHEX('00000000000000000000000000000B0B'), '00000000-0000-0000-0000-000000000b0b', 0),
  (3, 'BUSINESS',   UNHEX('0000000000000000000000000000A1CE'), 'Acme Corp', 0),
  (4, 'SYSTEM',     UNHEX('00000000000000000000000000005457'), 'Treasury', 0),
  (5, 'GOVERNMENT', UNHEX('0000000000000000000000000000601C'), 'City Hall', 0),
  (6, 'PERSONAL',   UNHEX('0000000000000000000000000000CA01'), '00000000-0000-0000-0000-00000000ca01', 1),
  (7, 'PERSONAL',   UNHEX('0000000000000000000000000000DA7E'), '00000000-0000-0000-0000-00000000da7e', 0);

-- Balances. Active personal: 1=5000, 2=100000, 7=1500 → personalSupply 106500.
-- totalSupply (!=SYSTEM, active) = 5000+100000+25000+8000+1500 = 139500. SYSTEM is negative.
INSERT INTO account_balances_mat (account_id, balance) VALUES
  (1, 5000.00), (2, 100000.00), (3, 25000.00), (4, -3000.00), (5, 8000.00), (6, 200.00), (7, 1500.00);

-- Firm "Acme Corp": Alice=Owner(ADMIN), Bob=Finance(FINANCIAL), Carol=Worker(DEFAULT only).
INSERT INTO firm (firm_id, display_name, proprietor_uuid_bin, hq_region, is_archived) VALUES
  (1, 'Acme Corp', UNHEX('0000000000000000000000000000A1CE'), 'NewHamilton', 0),
  (2, 'TaxFree Co', UNHEX('0000000000000000000000000000DA7E'), 'NewHamilton', 0);

-- Balance-tax exemption (business plugin firm_properties): firm 2 is actively
-- exempt; firm 1 has a soft-deleted exempt flag that must be ignored.
INSERT INTO firm_properties (firm_id, `key`, value, type, deleted_at) VALUES
  (2, 'balance-tax.exempt', 'true', 'BOOLEAN', NULL),
  (1, 'balance-tax.exempt', 'true', 'BOOLEAN', NOW());

INSERT INTO firm_role (role_id, firm_id, name, rank_order, is_proprietor_like, is_default) VALUES
  (1, 1, 'Owner', 0, 1, 0),
  (2, 1, 'Finance', 1, 0, 0),
  (3, 1, 'Worker', 2, 0, 1);

INSERT INTO firm_role_permission (role_id, permission) VALUES
  (1, 'ADMIN'), (1, 'FINANCIAL'), (1, 'CHESTSHOP'), (1, 'DEFAULT'),
  (2, 'FINANCIAL'), (2, 'DEFAULT'),
  (3, 'DEFAULT');

INSERT INTO firm_employee (firm_id, player_uuid_bin, role_id, joined_at) VALUES
  (1, UNHEX('0000000000000000000000000000A1CE'), 1, NOW()),
  (1, UNHEX('00000000000000000000000000000B0B'), 2, NOW()),
  (1, UNHEX('0000000000000000000000000000CA01'), 3, NOW());

INSERT INTO firm_accounts (firm_id, account_id) VALUES (1, 3);

-- Ledger: txn1 cross-type 2-leg (Alice personal -> Acme business),
-- txn2 same-type personal->personal, txn3 a 3-leg split (must be ignored by money-flow).
INSERT INTO ledger_txns (txn_id, settlement_time, message, initiator_uuid_bin, plugin_system) VALUES
  (1, NOW(), 'pay acme', UNHEX('0000000000000000000000000000A1CE'), 'treasury'),
  (2, NOW(), 'p2p',      UNHEX('0000000000000000000000000000A1CE'), 'treasury'),
  (3, NOW(), 'split',    UNHEX('00000000000000000000000000000B0B'), 'treasury');

INSERT INTO ledger_postings (txn_id, account_id, amount) VALUES
  (1, 1, -500.00), (1, 3, 500.00),
  (2, 1, -100.00), (2, 2, 100.00),
  (3, 2, -300.00), (3, 1, 100.00), (3, 3, 200.00);

-- ChestShop sales: Alice's personal shop (#1) and Acme's firm shop (#3).
INSERT INTO chestshop_sale (txn_id, occurred_at, direction, customer_uuid_bin, shop_account_id, shop_account_type, shop_firm_id, shop_owner_uuid_bin, is_admin_shop, material, item_key, item_name, item_custom, quantity, unit_price, total_price) VALUES
  (NULL, NOW(), 'SELL', UNHEX('00000000000000000000000000000B0B'), 1, 'PERSONAL', NULL, UNHEX('0000000000000000000000000000A1CE'), 0, 'DIAMOND', 'DIAMOND', 'Diamond', 0, 10, 5.0000, 50.00),
  (NULL, NOW(), 'SELL', UNHEX('0000000000000000000000000000CA01'), 3, 'BUSINESS', 1, UNHEX('0000000000000000000000000000A1CE'), 0, 'DIAMOND', 'DIAMOND', 'Diamond', 0, 5, 6.0000, 30.00),
  (NULL, NOW(), 'SELL', UNHEX('00000000000000000000000000000B0B'), 3, 'BUSINESS', 1, UNHEX('0000000000000000000000000000A1CE'), 0, 'IRON_INGOT', 'IRON_INGOT', 'Iron Ingot', 0, 20, 1.0000, 20.00);

-- Explorer RBAC groups: "Auditors" grants staff.audit and is LuckPerms-fed (node
-- 'doc'); Bob is a manual member. Used to assert findCapabilities + isStaff.
INSERT INTO explorer_group (group_id, name, description, luckperms_node) VALUES
  (1, 'Auditors', 'Staff who can audit any entity', 'doc');
INSERT INTO explorer_group_capability (group_id, capability) VALUES
  (1, 'staff.audit');
INSERT INTO explorer_group_member (group_id, player_uuid_bin, source) VALUES
  (1, UNHEX('00000000000000000000000000000B0B'), 'manual');

-- Bedrock/Floodgate fixture (PAR-240): a Floodgate-shaped UUID with a
-- '.'-prefixed name and a completed in-game explorer_identity link. Account #8
-- carries a $0 balance on purpose so it can't perturb the supply / balance /
-- distribution / gini assertions above (those count only positive, active
-- personal balances). Proves a linked Bedrock player's wallet resolves the
-- dotted name (never a bare UUID) and is searchable by it.
INSERT INTO firm_players (player_uuid_bin, current_name) VALUES
  (UNHEX('0000000000000000000000000000BED0'), '.BedrockBob');
INSERT INTO accounts (account_id, account_type, owner_uuid_bin, display_name, is_archived) VALUES
  (8, 'PERSONAL', UNHEX('0000000000000000000000000000BED0'), '00000000-0000-0000-0000-00000000bed0', 0);
INSERT INTO account_balances_mat (account_id, balance) VALUES (8, 0.00);
INSERT INTO explorer_identity (keycloak_sub, player_uuid_bin, minecraft_name, linked_by) VALUES
  ('e2e-bedrock', UNHEX('0000000000000000000000000000BED0'), '.BedrockBob', 'in-game:.BedrockBob');

-- Government department viewer (PAR-237): a "secretary" granted read-only view of
-- government account #5 (City Hall) — not its owner, not a member. Drives the
-- canReadAccount web gate that lets them see the department's ledger history.
INSERT INTO account_viewers (account_id, viewer_uuid_bin, added_by_uuid_bin) VALUES
  (5, UNHEX('00000000000000000000000000005EC0'), UNHEX('0000000000000000000000000000A1CE'));
