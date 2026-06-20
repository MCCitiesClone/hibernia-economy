-- ChestShop sales tracker + live shop registry (see wiki/chestshop-sales-tracker.md).
--
-- Replaces the old "parse the ledger transaction message with a regex" approach
-- that powered the Market UI, and supersedes business-rian's stubbed firm_sale.
-- Two tables, both written by ChestShop-3 and read by treasury-rest-api:
--   * chestshop_sale  — one structured row per trade (analytics/history)
--   * chestshop_shop  — one row per shop sign (live "what's for sale / in stock")
--
-- These are an analytics index, NOT a money store — money stays authoritative in
-- the ledger; chestshop_sale.txn_id links back to it.

-- One row per ChestShop transaction.
CREATE TABLE chestshop_sale (
    sale_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    txn_id            BIGINT UNSIGNED NULL,            -- link to ledger_txns (reconcile); NULL if unlinkable
    occurred_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    direction         ENUM('BUY','SELL') NOT NULL,     -- from the CUSTOMER's perspective (ChestShop semantics)

    -- the customer (player who clicked the sign)
    customer_uuid_bin BINARY(16)      NOT NULL,

    -- the shop side (the sign owner; the account money routes to/from)
    shop_account_id     INT UNSIGNED  NULL,            -- NULL for admin shops
    shop_account_type   ENUM('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NULL,
    shop_firm_id        INT           NULL,            -- set IFF BUSINESS (via firm_accounts)
    shop_owner_uuid_bin BINARY(16)    NULL,            -- set for PERSONAL (the owning player)
    is_admin_shop       TINYINT(1)    NOT NULL DEFAULT 0,

    -- item (rich: ChestShop supports custom items, so material alone is not enough)
    material          VARCHAR(64)     NOT NULL,        -- Bukkit Material — coarse grouping/filter
    item_key          VARCHAR(190)    NOT NULL,        -- stable identity of the ACTUAL item (custom id ▸ chestshop code ▸ material)
    item_name         VARCHAR(255)    NOT NULL,        -- human-readable label for the UI
    item_custom       TINYINT(1)      NOT NULL DEFAULT 0,
    item_data         MEDIUMTEXT      NULL,            -- base64 ItemStack for full fidelity; set when item_custom

    -- price
    quantity          INT             NOT NULL,
    unit_price        DECIMAL(19,4)   NOT NULL,        -- total / quantity, captured precisely
    total_price       DECIMAL(19,2)   NOT NULL,
    tax_amount        DECIMAL(19,2)   NOT NULL DEFAULT 0.00,

    -- where (the shop's sign location)
    world             VARCHAR(64)     NULL,
    sign_x INT NULL, sign_y INT NULL, sign_z INT NULL,

    -- shop stock of the traded item, measured at sale time (post-trade); NULL for
    -- admin/infinite shops. Pre-trade is derivable: BUY → +quantity, SELL → −quantity.
    shop_stock        INT             NULL,

    PRIMARY KEY (sale_id),
    KEY idx_cs_time      (occurred_at),
    KEY idx_cs_material  (material, occurred_at),
    KEY idx_cs_item      (item_key, occurred_at),
    KEY idx_cs_acct      (shop_account_id, occurred_at),
    KEY idx_cs_firm      (shop_firm_id, occurred_at),
    KEY idx_cs_owner     (shop_owner_uuid_bin, occurred_at),
    KEY idx_cs_customer  (customer_uuid_bin, occurred_at),
    KEY idx_cs_txn       (txn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per shop sign — the live "which shops sell X, where, how much" index.
-- ChestShop keeps no global index of shop locations, so this table is it.
CREATE TABLE chestshop_shop (
    shop_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- identity = the sign location
    world            VARCHAR(64)  NOT NULL,
    sign_x INT NOT NULL, sign_y INT NOT NULL, sign_z INT NOT NULL,

    -- owner account (same model as chestshop_sale)
    is_admin_shop       TINYINT(1)   NOT NULL DEFAULT 0,
    shop_account_id     INT UNSIGNED NULL,
    shop_account_type   ENUM('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NULL,
    shop_firm_id        INT          NULL,
    shop_owner_uuid_bin BINARY(16)   NULL,

    -- what it trades (same item model as chestshop_sale)
    material         VARCHAR(64)  NOT NULL,
    item_key         VARCHAR(190) NOT NULL,
    item_name        VARCHAR(255) NOT NULL,
    item_custom      TINYINT(1)   NOT NULL DEFAULT 0,
    item_data        MEDIUMTEXT   NULL,

    -- sign pricing
    buy_price        DECIMAL(19,2) NULL,               -- customer BUYS from shop (needs stock); NULL if not offered
    sell_price       DECIMAL(19,2) NULL,               -- customer SELLS to shop; NULL if not offered
    batch_qty        INT          NOT NULL,            -- units per transaction (sign line 1)

    -- live stock
    current_stock    INT          NULL,                -- units of the item in the chest; NULL = admin/infinite or never-read

    -- bookkeeping
    active           TINYINT(1)   NOT NULL DEFAULT 1,  -- 0 once destroyed (kept for history)
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stock_at         TIMESTAMP    NULL,                -- when current_stock was last measured
    last_seen        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (shop_id),
    UNIQUE KEY uq_shop_location (world, sign_x, sign_y, sign_z),
    KEY idx_shop_item   (item_key, active),
    KEY idx_shop_mat    (material, active),
    KEY idx_shop_firm   (shop_firm_id),
    KEY idx_shop_owner  (shop_owner_uuid_bin),
    KEY idx_shop_acct   (shop_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
