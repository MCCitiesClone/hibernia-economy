# Monorepo Quality Audit — Report

> **Read-only audit.** No source was modified. Upgrades/rewrites are captured as findings with remediation plans for a separate run.

## Scoring formula

```
score(component, dimension) = max(0, round(10 − Σ weight(f), 1))
weight: blocker=3.0, major=1.5, minor=0.5, nit=0.1
confidence multiplier: high×1.0, medium×0.7, low×0.4
Global dimensions (build/infra/dependencies/database) score once, repeated per component row.
```

## 1. Score matrix

| Component | structure | plugin-architecture | behaviour | testing | build | infra | dependencies | database | **mean** |
|---|---|---|---|---|---|---|---|---|---|
| common | 9.9 | — | 9.5 | 8.0 | 7.5 | 7.5 | 8.2 | 8.7 | **8.5** |
| treasury | 5.5 | 7.9 | 10.0 | 6.0 | 7.5 | 7.5 | 8.2 | 8.7 | **7.7** |
| business | 7.9 | 3.9 | 9.2 | 7.0 | 7.5 | 7.5 | 8.2 | 8.7 | **7.5** |
| treasury-api-plugin | 7.4 | 5.5 | 9.6 | 2.7 | 7.5 | 7.5 | 8.2 | 8.7 | **7.1** |
| treasury-rest-api | 8.4 | — | 9.3 | 3.3 | 7.5 | 7.5 | 8.2 | 8.7 | **7.6** |
| chestshop | 5.0 | 4.9 | 7.2 | 7.5 | 7.5 | 7.5 | 8.2 | 8.7 | **7.1** |
| economy-explorer | 9.2 | — | 9.5 | 4.9 | 7.5 | 7.5 | 8.2 | 8.7 | **7.9** |
| **mean** | **7.6** | **5.5** | **9.2** | **5.6** | **7.5** | **7.5** | **8.2** | **8.7** |  |

## 2. Executive summary

- **Total findings (post-verification, merged):** 125
- **Blockers:** 0  |  **Major:** 25  |  **Minor:** 86  |  **Nit:** 14
- **Rejected in verification:** 1

### Top 10 findings by weight

| # | weight | severity | conf | component/dim | file:lines | description |
|---|---|---|---|---|---|---|
| 1 | 1.50 | major | high | business/plugin-architecture | `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:104-115` | Finance command handlers catch service-thrown exceptions and hand-format the error to a Message key, which the charter explicitly bans ("fla… |
| 2 | 1.50 | major | high | business/plugin-architecture | `business/src/main/java/io/paradaux/business/commands/resolvers/OnlineFirmNameResolver.java:43-51` | ParameterResolver.suggestions() runs off the main thread (the code's own comment says 'Suggestions run on a Netty thread'), yet it reads liv… |
| 3 | 1.50 | major | high | business/plugin-architecture | `business/src/main/java/io/paradaux/business/services/impl/FirmTransactionServiceImpl.java:74-87` | FirmTransactionServiceImpl signals all user-facing business/validation failures (invalid amount, insufficient funds, missing authorization/a… |
| 4 | 1.50 | major | high | business/testing | `business/src/main/java/io/paradaux/business/Business.java:128-128` | No startup-shaped test builds the Guice injector (HiberniaModule + BusinessModule + DatabaseModule) and calls registerAll() on CommandManage… |
| 5 | 1.50 | major | high | chestshop/behaviour | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/ShopServiceImpl.java:589-609` | refundOnRemoval credits the shop owner FIRST (economy.deposit, which is a SYSTEM→owner transfer, a mint) and only afterwards debits the serv… |
| 6 | 1.50 | major | high | chestshop/plugin-architecture | `chestshop/src/main/java/io/paradaux/chestshop/listeners/PhysicsBreakListener.java:18-21` | Listener registered on BlockPhysicsEvent (one of the charter's named hot events, fired for essentially every block-update in loaded chunks).… |
| 7 | 1.50 | major | high | chestshop/structure | `chestshop/src/main/java/io/paradaux/chestshop/listeners/MarketListener.java:44-117` | Four classes in listeners/ (entrypoint layer) hold business-layer logic that is invoked directly by the service layer, and are injected into… |
| 8 | 1.50 | major | high | chestshop/structure | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:89-140` | TransactionServiceImpl is a god class: 719 lines and 17 injected constructor dependencies (well above the ~10 charter threshold). It owns pr… |
| 9 | 1.50 | major | high | chestshop/testing | `chestshop/src/main/java/io/paradaux/chestshop/ChestShop.java:144-156` | No startup-shaped test exists that builds the Guice module (ChestShopModule) and calls CommandManager.registerAll()/ListenerManager.register… |
| 10 | 1.50 | major | high | economy-explorer/testing | `lib/util/ssrf.ts:42-73` | lib/util/ssrf.ts is the SSRF guard for player-supplied webhook URLs (the first line of defence against a player pointing a webhook at 127.0.… |

## 3. Findings by component → dimension

## Component: common  (mean 8.5)

### common · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| common/structure/0001 | nit | high | confirmed | `common/src/main/java/io/paradaux/common/BalanceTaxBrackets.java:56-72` | The public factory `fromRawEntries` treats `rawEntries` as nullable (explicit `!= null` guard at line 58) but has no null contract for `onWarn`: if a caller passes `null` and any entry is invalid (out… | Either fail fast at the top of the method with `Objects.requireNonNull(onWarn, "onWarn")` to make the required contract explicit and give a clear message, or document `onWarn` as `@NonNull` in the jav… | trivial |

<details><summary>snippets</summary>

**common/structure/0001** `common/src/main/java/io/paradaux/common/BalanceTaxBrackets.java:56-72`

```
public static BalanceTaxBrackets fromRawEntries(Map<String, String> rawEntries, Consumer<String> onWarn) {
        NavigableMap<BigDecimal, BigDecimal> map = new TreeMap<>();
        if (rawEntries != null) {
            for (Map.Entry<String, String> e : rawEntries.entrySet()) {
                String key = e.getKey();
                try {
                    BigDecimal min = new BigDecimal(key);
                    BigDecimal rate = new BigDecimal(e.getValue() == null ? "0" : e.getValue());
                    if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                        onWarn.accept("Balance tax bracket rate for floor '" + key
                                + "' is out of range [0,1]: " + rate + " — skipping");
                        continue;
                    }
                    map.put(min, rate);
                } catch (NumberFormatException ex) {
                    onWarn.accept("Invalid balance tax bracket key '" + key + "' — skipping");
                }
```

</details>

### common · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| common/behaviour/0001 | minor | high | confirmed | `common/src/main/java/io/paradaux/common/BalanceTaxBrackets.java:61-72` | When a bracket entry's key parses as a valid numeric floor but its rate VALUE is unparseable (e.g. an empty string, or a typo like "5%"), the NumberFormatException from `new BigDecimal(e.getValue())` … | Split the parse: catch the key parse and value parse separately (or inspect which token failed) so the warning names the offending field, e.g. "Invalid balance tax bracket rate '<value>' for floor '<k… | small |

<details><summary>snippets</summary>

**common/behaviour/0001** `common/src/main/java/io/paradaux/common/BalanceTaxBrackets.java:61-72`

```
                    BigDecimal rate = new BigDecimal(e.getValue() == null ? "0" : e.getValue());
                    if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                        onWarn.accept("Balance tax bracket rate for floor '" + key
                                + "' is out of range [0,1]: " + rate + " — skipping");
                        continue;
                    }
                    map.put(min, rate);
                } catch (NumberFormatException ex) {
                    onWarn.accept("Invalid balance tax bracket key '" + key + "' — skipping");
```

</details>

### common · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| common/testing/0001 | minor | high | downgraded | `common/src/main/java/io/paradaux/common/DataSourceProvider.java:26-116` | DataSourceProvider has no test class at all (common/src/test has only BalanceTaxBrackets, JwtKeys, OverdraftPolicy, UuidBin). This is the config-drift-prevention class ADT-184 created to consolidate t… | Add DataSourceProviderTest asserting: default URL contains the mariadb/host/port/db and useUnicode=true&characterEncoding=utf8; statementCaching(true) appends the server-prep-stmt/cache/rewriteBatched… | medium |
| common/testing/0003 | minor | high | unverified | `common/src/main/java/io/paradaux/common/JwtKeys.java:46-49` | JwtKeysTest does not cover the null/blank rejection branch (lines 47-49). This is input validation on the single cross-trust-boundary derivation used by both mint (treasury-api-plugin) and verify (tre… | Add tests asserting deriveHmacKey(null), deriveHmacKey(""), and deriveHmacKey("   ") each throw IllegalStateException with the "JWT secret must be set" contract. | trivial |
| common/testing/0002 | minor | high | unverified | `common/src/main/java/io/paradaux/common/UuidBin.java:29-32` | UuidBinTest (only roundTripsAnyUuid and nullSafe) has no test for the ADT-147 wrong-length guard. This guard is an explicit, commented regression fix (lines 26-31: a non-16-byte buffer previously thre… | Add tests asserting UuidBin.fromBytes(new byte[15]) and fromBytes(new byte[17]) each throw IllegalArgumentException (not BufferUnderflowException and not a truncated UUID), pinning the ADT-147 fix. | trivial |
| common/testing/0004 | minor | high | unverified | `common/src/test/java/io/paradaux/common/JwtKeysTest.java:21-35` | The legacy passphrase path (JwtKeys lines 67-70: SecretKeySpec of MessageDigest SHA-256(secret UTF-8)) is only exercised indirectly — the tests assert it is deterministic, 32 bytes, and HmacSHA256, bu… | Add a test that pins the legacy derivation to a fixed vector: compute MessageDigest.getInstance("SHA-256").digest(secret.getBytes(UTF_8)) for a constant passphrase and assertArrayEquals it against der… | small |

<details><summary>snippets</summary>

**common/testing/0001** `common/src/main/java/io/paradaux/common/DataSourceProvider.java:26-116`

```
public final class DataSourceProvider implements AutoCloseable {

    private final HikariDataSource ds;

    private DataSourceProvider(Builder b) {
```

**common/testing/0003** `common/src/main/java/io/paradaux/common/JwtKeys.java:46-49`

```
    public static SecretKey deriveHmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be set");
        }
```

**common/testing/0002** `common/src/main/java/io/paradaux/common/UuidBin.java:29-32`

```
        if (bytes.length != 16) {
            throw new IllegalArgumentException(
                    "UUID BINARY value must be exactly 16 bytes, got " + bytes.length);
        }
```

**common/testing/0004** `common/src/test/java/io/paradaux/common/JwtKeysTest.java:21-35`

```
    void deriveHmacKey_isDeterministic_andProducesA256BitHmacKey() {
        SecretKey a = JwtKeys.deriveHmacKey("a-long-enough-shared-secret-value-123456");
        SecretKey b = JwtKeys.deriveHmacKey("a-long-enough-shared-secret-value-123456");
```

</details>

## Component: treasury  (mean 7.7)

### treasury · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury/structure/0001 | major | high | confirmed | `treasury/src/main/java/io/paradaux/treasury/utils/Idempotency.java:1-18` | There are two classes with the identical fully-qualified name io.paradaux.treasury.utils.Idempotency — one in the plugin (treasury/src/main) and one in the published treasury-api jar (treasury/treasur… | Delete the plugin's io.paradaux.treasury.utils.Idempotency and import the treasury-api io.paradaux.treasury.utils.Idempotency at the 5 call sites (GovServiceImpl, BalanceTaxService, LedgerServiceImpl,… | small |
| treasury/structure/0006 | minor | high | confirmed | `treasury/src/main/java/io/paradaux/treasury/api/impl/TaxApiImpl.java:272-291` | The 'create a primitive GOVERNMENT account (owner=VIRTUAL_TREASURY_OWNER, allowOverdraft=true, creditLimit=-1 sentinel) then seedBalance' block is reimplemented in TaxApiImpl.resolveOrCreateTaxAccount… | Route TaxApiImpl's default-tax-account creation through LedgerService.getOrCreateGovernmentAccount (or a shared AccountService method) instead of building the Account and calling AccountMapper inline,… | medium |
| treasury/structure/0007 | minor | medium | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/AccountResolver.java:71-82` | The business-account name resolution that tries the bare token then falls back to '<token> Corporate Account' (a hardcoded knowledge of FirmServiceImpl's display-name convention) is duplicated in Acco… | Centralise business-account-by-name resolution (bare + ' Corporate Account' fallback) in AccountService or reuse AccountResolver from GovCommand.doExternalPayout, so the ' Corporate Account' conventio… | small |
| treasury/structure/0002 | minor | high | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:995-997` | The MiniMessage tag-injection escape sanitize(String) is copy-pasted verbatim in three command classes (GovCommand, FineCommand, TransactionsCommand). It is a security-relevant helper (escapes user-su… | Extract sanitize() into a shared helper (e.g. a static method on a new commands util or on the Message/i18n layer) and call it from all three command classes so the injection-escape rule lives in exac… | small |
| treasury/structure/0003 | minor | high | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:687-703` | The per-transaction-entry rendering loop (formatted+signed+colored amount, memo fallback, sanitize, settlement-time formatting, and the treasury.transactions.entry message send) is duplicated near-ver… | Extract a single shared transaction-entry renderer (a small helper class in commands/ taking Message + AccountService, or a method on a shared base) and have both GovCommand and TransactionsCommand ca… | small |
| treasury/structure/0005 | minor | high | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:983-993` | Two sender-related helpers are reimplemented across the command layer. (1) The 'actor UUID from sender' idiom (player UUID, else VIRTUAL_TREASURY_INITIATOR) exists as a private static actorOf in GovCo… | Add a small shared command-support helper (or static methods) for actorOf(CommandSender) and resolvePlayerName(UUID) and use it from GovCommand, TreasuryCommand, EcoCommand and FineCommand instead of … | small |
| treasury/structure/0004 | minor | high | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/PayCommand.java:124-205` | The 'pay from the sender's personal account' orchestration — resolve/create sender account, read balance, pre-check against normalized amount, compose a 'Payment from X to Y[: memo]' memo, transfer, c… | Extract the shared 'debit sender personal account into target account id' flow (balance pre-check, memo composition, transfer, insufficient-funds handling, success/notify) into one method (a service m… | medium |
| treasury/structure/0008 | nit | high | unverified | `treasury/src/main/java/io/paradaux/treasury/api/impl/TreasuryApiImpl.java:27-39` | TreasuryApiImpl injects the concrete TaxApiImpl instead of the TaxApi interface, and its getTaxApi() field is typed TaxApiImpl. Everywhere else in the module DI is done against interfaces (AccountServ… | Change the field and constructor parameter to the TaxApi interface (it is already bound in TreasuryModule) for consistency with the other injected services. | trivial |

<details><summary>snippets</summary>

**treasury/structure/0001** `treasury/src/main/java/io/paradaux/treasury/utils/Idempotency.java:1-18`

```
public final class Idempotency {
    private Idempotency() {
    }

    public static byte[] sha256(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

**treasury/structure/0006** `treasury/src/main/java/io/paradaux/treasury/api/impl/TaxApiImpl.java:272-291`

```
    private int resolveOrCreateTaxAccount() {
        String accountName = getDefaultTaxAccountName();
        Account existing = accountMapper.findGovernmentAccountByName(accountName);
        if (existing != null) return existing.getAccountId();

        Account account = new Account();
        account.setAccountType(AccountType.GOVERNMENT);
        account.setOwnerUuid(TreasuryConstants.VIRTUAL_TREASURY_OWNER);
        account.setDisplayName(accountName);
        account.setRequiresAuthorization(false);
        account.setArchived(false);
        account.setAllowOverdraft(true);
        // Primitive GOVERNMENT account: -1 = unlimited credit (faucet/sink).
        account.setCreditLimit(BigDecimal.valueOf(-1));
        accountMapper.insertAccount(account);
        accountMapper.seedBalance(account.getAccountId());
        ...
        return account.getAccountId();
    }
```

**treasury/structure/0007** `treasury/src/main/java/io/paradaux/treasury/commands/AccountResolver.java:71-82`

```
            case "business", "firm" -> {
                Account a = accountService.getBusinessAccountByName(token);
                if (a == null) {
                    a = accountService.getBusinessAccountByName(token + " Corporate Account");
                }
                if (a == null) {
                    sender.sendMessage("§cUnknown business account for <" + side + ">: " + token ...);
                    return null;
                }
                return new Resolved(a.getAccountId(), "business " + a.getDisplayName());
            }
```

**treasury/structure/0002** `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:995-997`

```
    private static String sanitize(String input) {
        return input.replace("<", "\\<");
    }
```

**treasury/structure/0003** `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:687-703`

```
        for (TransactionEntry entry : result.items()) {
            String formattedAmount = accountService.formatAmount(entry.getAmount().abs());
            String sign = entry.getAmount().signum() >= 0 ? "+" : "-";
            String colorTag = entry.getAmount().signum() >= 0 ? "green" : "red";
            String coloredAmount = "<" + colorTag + ">" + sign + formattedAmount + "</" + colorTag + ">";
            String memo = entry.getMemo() != null ? entry.getMemo() : entry.getMessage();
            if (memo == null) memo = "—";
            memo = sanitize(memo);
            String time = entry.getSettlementTime() != null
                    ? TIME_FMT.format(entry.getSettlementTime()) : "—";

            message.send(sender, "treasury.transactions.entry",
                    "txn", String.valueOf(entry.getTxnId()),
                    "amount", coloredAmount,
                    "memo", memo,
                    "time", time);
        }
```

**treasury/structure/0005** `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:983-993`

```
    private static UUID actorOf(CommandSender sender) {
        return sender instanceof Player p
                ? p.getUniqueId()
                : TreasuryConstants.VIRTUAL_TREASURY_INITIATOR;
    }

    private String resolvePlayerName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString();
    }
```

**treasury/structure/0004** `treasury/src/main/java/io/paradaux/treasury/commands/PayCommand.java:124-205`

```
    private void payPlayer(Player sender, UUID targetUuid, String targetName, BigDecimal normalized, String memo) {
        ...
        int senderAccountId = accountService.getOrCreatePersonalAccountId(sender.getUniqueId());
        BigDecimal senderBalance = accountService.getBalanceReadOnly(senderAccountId);
        if (senderBalance.compareTo(normalized) < 0) {
            message.send(sender, "treasury.pay.insufficient", ...);
            return;
        }
        ...
        try {
            ledgerService.transfer(new TransferRequest(...));
        } catch (IllegalStateException e) {
            BigDecimal currentBalance = accountService.getBalanceReadOnly(senderAccountId);
            message.send(sender, "treasury.pay.insufficient", ...);
            return;
        }
```

**treasury/structure/0008** `treasury/src/main/java/io/paradaux/treasury/api/impl/TreasuryApiImpl.java:27-39`

```
    private final TaxApiImpl taxApi;

    @Inject
    public TreasuryApiImpl(AccountService accountService,
                           MembershipService membershipService,
                           LedgerService ledgerService,
                           DataExportService dataExportService,
                           TaxApiImpl taxApi) {
        ...
        this.taxApi = taxApi;
    }
```

</details>

### treasury · plugin-architecture

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury/plugin-architecture/0004 | minor | medium | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:948-959` | The membership/authorizer-based spend authorization for GOVERNMENT accounts lives entirely in the command layer (GovCommand.canTransferFrom / hasAuthorizerAccess / isAllowed and FineCommand.canFineFro… | Move the per-account member/authorizer spend check into the service layer (e.g. a GovService/LedgerService method that takes the acting UUID and throws NoPermissionException), and have the handlers de… | medium |
| treasury/plugin-architecture/0002 | minor | high | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/TaxCommand.java:80-159` | TaxCommand's /tax status and /tax trigger handlers build all player-facing output from hardcoded MiniMessage strings sent via raw sender.sendRichMessage(...) rather than Message keys. Roughly two doze… | Move these strings into messages.properties as treasury.tax.status.* / treasury.tax.trigger.* keys and emit them via message.send(...). Where dynamic rows are assembled (formatCycleRow, formatNextFire… | medium |
| treasury/plugin-architecture/0001 | minor | high | confirmed | `treasury/src/main/java/io/paradaux/treasury/commands/TreasuryCommand.java:81-84` | Multiple admin/debug command handlers bypass the framework i18n layer: they emit hardcoded English strings with legacy §-section colour codes through raw sender.sendMessage(...) / sendRichMessage(...)… | Route every player-facing line through Message.send(sender, "treasury.<key>", ...) with the strings defined in messages.properties, matching PayCommand/BalanceCommand/GovCommand. Remove all §-codes; u… | medium |
| treasury/plugin-architecture/0003 | minor | medium | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/resolvers/PayTargetResolver.java:58-68` | The charter states resolver suggestions(...) runs off the main thread and must never touch live Bukkit state (Bukkit.getPlayer, world/entity access), being backed by service-managed caches instead. Pa… | Serve online-player suggestions from a service-managed cache updated on join/quit (or snapshot the roster on the main thread and hand the resolver an immutable copy), mirroring how governmentNames() i… | small |
| treasury/plugin-architecture/0005 | minor | medium | unverified | `treasury/src/main/java/io/paradaux/treasury/services/BalanceTaxService.java:42-42` | The charter calls for an interface + impl split for every service. Three service-package classes are concrete-only with no interface: BalanceTaxService (tax computation/collection, injects AccountMapp… | Extract at least BalanceTaxService behind an interface (BalanceTaxService/BalanceTaxServiceImpl) bound in TreasuryModule, matching the rest of the service layer. TaxCycleRegistry (a mutable-state regi… | medium |
| treasury/plugin-architecture/0006 | nit | medium | confirmed | `treasury/src/main/java/io/paradaux/treasury/events/PlayerLoginListener.java:54-55` | PlayerLoginListener handles PlayerJoinEvent at EventPriority.MONITOR while dispatching state-mutating work (recordLogin writes economy_players; balance tax collection writes the ledger). The charter f… | Use the default (NORMAL) priority for a side-effecting join handler, reserving MONITOR for read-only observation; align both PlayerJoinEvent handlers on a deliberate, documented priority. | trivial |

<details><summary>snippets</summary>

**treasury/plugin-architecture/0004** `treasury/src/main/java/io/paradaux/treasury/commands/GovCommand.java:948-959`

```
    private boolean canTransferFrom(CommandSender sender, Account account) {
        if (!(sender instanceof Player p)) {
            return true;
        }
        return p.hasPermission("treasury.gov.admin")
                || p.hasPermission("treasury.gov.account.transfer")
                || membershipService.isMember(account.getAccountId(), p.getUniqueId())
                || membershipService.isAuthorizer(account.getAccountId(), p.getUniqueId());
```

**treasury/plugin-architecture/0002** `treasury/src/main/java/io/paradaux/treasury/commands/TaxCommand.java:80-159`

```
sender.sendRichMessage("<bold><blue>TREASURY</blue></bold> <gray>»</gray> Tax Status");
```

**treasury/plugin-architecture/0001** `treasury/src/main/java/io/paradaux/treasury/commands/TreasuryCommand.java:81-84`

```
sender.sendMessage("§cReload failed: " + e.getMessage() + " (see console).");
            return;
        }
        sender.sendMessage("§aReloaded config.yml and messages.properties "
```

**treasury/plugin-architecture/0003** `treasury/src/main/java/io/paradaux/treasury/commands/resolvers/PayTargetResolver.java:58-68`

```
    public List<String> suggestions(String prefix, CommandSender sender) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
```

**treasury/plugin-architecture/0005** `treasury/src/main/java/io/paradaux/treasury/services/BalanceTaxService.java:42-42`

```
public class BalanceTaxService {
```

**treasury/plugin-architecture/0006** `treasury/src/main/java/io/paradaux/treasury/events/PlayerLoginListener.java:54-55`

```
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
```

</details>

### treasury · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury/behaviour/0001 | nit | low | downgraded | `treasury/src/main/java/io/paradaux/treasury/services/impl/SalaryServiceImpl.java:126-141` | The per-run salary dedup key buckets wall-clock time by the configured interval (currentPeriodStart = now - now mod interval), while SalaryTask.schedule fires a Bukkit timer every intervalSeconds tick… | Either derive the salary run id from the same monotonic scheduler tick/sequence that drives SalaryTask (so bucketing and firing share one clock), or key the dedup on an explicit incrementing run count… | small |

<details><summary>snippets</summary>

**treasury/behaviour/0001** `treasury/src/main/java/io/paradaux/treasury/services/impl/SalaryServiceImpl.java:126-141`

```
long runId = currentPeriodStart();

        int paid = 0;
        for (SalaryPayment payment : payments) {
            try {
                int toAccountId = accountService.getOrCreatePersonalAccountId(payment.player());
                byte[] dedupKey = Idempotency.sha256("salary:" + runId + ":" + payment.player());
```

</details>

### treasury · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury/testing/0002 | major | high | unverified | `treasury/src/main/java/io/paradaux/treasury/commands/resolvers/PayTargetResolver.java:57-82` | PayTargetResolver is the only custom ParameterResolver in the component and has no test (no *Resolver*Test.java exists under treasury/src/test). Its suggestions() has real, testable logic: prefix filt… | Add a PayTargetResolverTest: mockStatic(Bukkit) for online players, stub AccountService.listGovernmentAccounts(); assert prefix filtering across players+gov names, the 20-suggestion cap, that archived… | small |
| treasury/testing/0003 | major | high | unverified | `treasury/src/main/java/io/paradaux/treasury/services/cache/AccountRedirectCache.java:35-60` | The account-redirect chain is entirely untested despite being money-adjacent: AccountRedirectMapper (findAllRedirects/findRedirectAccountId/upsertRedirect) is referenced in zero test files; AccountRed… | Add a mapper IT that upserts and reads redirects against the real MariaDB, an AccountRedirectCache unit test covering warm/hit/authoritative-miss/reload, and a LedgerService IT that seeds an account_r… | medium |
| treasury/testing/0001 | minor | high | downgraded | `treasury/src/main/java/io/paradaux/treasury/Treasury.java:143-143` | No startup-shaped test builds the plugin's Guice/HiberniaModule graph and invokes CommandManager.registerAll() / ListenerManager.registerAll(). The charter requires such a test so that duplicate @Comm… | Add a wiring/startup test that constructs the real HiberniaModule + TreasuryModule injector (with a stub DataSource or the IT MariaDB), resolves CommandManager and ListenerManager, and calls registerA… | medium |
| treasury/testing/0004 | minor | medium | unverified | `treasury/src/test/java/io/paradaux/treasury/services/BalanceTaxServiceMockedApiTest.java:88-130` | The apiReturnsSkipped and apiReturnsFailed tests exercise the Skipped/Failed branches of BalanceTaxService.collect but assert only the interaction verify(taxApi).collectTax(...) — the same call the ha… | Assert the observable consequence of Skipped/Failed handling: that collect() returns/does not throw and performs no follow-up side effect that a successful collect would (e.g. verify no additional led… | small |
| treasury/testing/0005 | nit | high | unverified | `treasury/src/main/resources/messages.properties:6-6` | The message key treasury.general.player-only is defined in messages.properties but is never referenced by any code path (grep for 'player-only' across treasury/src/main returns only this definition, n… | Either remove the unused treasury.general.player-only key, or wire the player-only guard that was presumably intended to use it; add a bidirectional key-coverage assertion to the startup test so defin… | trivial |

<details><summary>snippets</summary>

**treasury/testing/0002** `treasury/src/main/java/io/paradaux/treasury/commands/resolvers/PayTargetResolver.java:57-82`

```
    public List<String> suggestions(String prefix, CommandSender sender) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
```

**treasury/testing/0003** `treasury/src/main/java/io/paradaux/treasury/services/cache/AccountRedirectCache.java:35-60`

```
    public Integer get(UUID uuid) {
        Map<UUID, Integer> map = redirects;
        if (map == null) {
            synchronized (this) {
```

**treasury/testing/0001** `treasury/src/main/java/io/paradaux/treasury/Treasury.java:143-143`

```
injector.getInstance(CommandManager.class).registerAll();
```

**treasury/testing/0004** `treasury/src/test/java/io/paradaux/treasury/services/BalanceTaxServiceMockedApiTest.java:88-130`

```
        svc.collect(player, lastLogin, now);

        verify(taxApi).collectTax(any(TaxCollection.class));
```

**treasury/testing/0005** `treasury/src/main/resources/messages.properties:6-6`

```
treasury.general.player-only
```

</details>

## Component: business  (mean 7.5)

### business · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| business/structure/0001 | minor | high | confirmed | `business/src/main/java/io/paradaux/business/commands/AccountCommands.java:586-590` | The firm-finance access predicate (proprietor OR ADMIN OR FINANCIAL) is copy-pasted verbatim across three command classes: AccountCommands.canAccessFirmFinances, MiscCommands.canAccessFirmFinances, an… | Extract a single shared predicate (e.g. a FirmStaffService.canAccessFinances(firmId, uuid) method, since FirmStaffService already owns hasPermission and is injected everywhere) and have all three comm… | small |
| business/structure/0002 | minor | high | unverified | `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:379-391` | The transaction-history rendering loop (sign/formatted/time/msg build + header + next-page footer) is duplicated near-verbatim between MiscCommands.transactions and AccountCommands.accountTransactions… | Hoist the per-entry formatting (sign + amount + time + message) and the TX_PAGE_SIZE / TIME_FMT constants into a shared helper (a small formatter utility or a base command class), parameterised by the… | small |
| business/structure/0004 | minor | high | unverified | `business/src/main/java/io/paradaux/business/services/FirmStaffService.java:12-17` | The same domain identity (a firm) is addressed by three different parameter types across the service surface: String firmName (which is actually a name-or-id string, parsed downstream by getFirmByName… | Standardise firm-addressing on int firmId in the service and API layer (matching the model's Integer firmId primary key), keeping String firmName only where a genuine name/id ambiguity from user input… | large |
| business/structure/0003 | minor | high | unverified | `business/src/main/java/io/paradaux/business/services/impl/FirmServiceImpl.java:322-324` | The Discord invite-URL validation regex "https://discord\\.gg/[A-Za-z0-9]{2,32}" is hard-coded in three separate places: FirmServiceImpl.updateFirmDiscord and both FirmCommands.setDiscord and FirmComm… | Move the pattern into a single validator (e.g. NameValidator.isValidDiscordUrl(String) alongside the existing name validators, which already centralises anti-injection whitelists) and call it from all… | small |
| business/structure/0005 | nit | high | unverified | `business/src/main/java/io/paradaux/business/services/impl/FirmStaffServiceImpl.java:254-254` | Objects.requireNonNull(roleName, ...) is dead defensive code: the immediately preceding line 252 already returns false when roleName is null or blank, so roleName is provably non-null (and non-blank) … | Drop the Objects.requireNonNull wrapper and call roleName.trim() directly; the guard on line 252 already establishes the invariant. | trivial |

<details><summary>snippets</summary>

**business/structure/0001** `business/src/main/java/io/paradaux/business/commands/AccountCommands.java:586-590`

```
private boolean canAccessFirmFinances(Firm firm, Player player) {
        return firms.isProprietor(firm.getFirmId(), player.getUniqueId())
                || staff.hasPermission(firm.getFirmId(), player.getUniqueId(), RolePermission.ADMIN)
                || staff.hasPermission(firm.getFirmId(), player.getUniqueId(), RolePermission.FINANCIAL);
    }
```

**business/structure/0002** `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:379-391`

```
for (TransactionEntry entry : txPage.items()) {
            String sign = entry.getAmount().signum() >= 0 ? "+" : "";
            String formatted = sign + treasury.formatAmount(entry.getAmount());
            String time = TIME_FMT.format(entry.getSettlementTime());
            String msg = entry.getMessage() != null ? entry.getMessage() : "";
            message.send(sender, "business.finance.transactions.line",
                    "time", time, "amount", formatted, "message", msg);
        }
```

**business/structure/0004** `business/src/main/java/io/paradaux/business/services/FirmStaffService.java:12-17`

```
    void fireEmployee(String firmName, UUID playerId, UUID actorId);
    String promoteEmployee(String firmName, UUID playerId, UUID actorId);
    String demoteEmployee(String firmName, UUID playerId, UUID actorId);
    void resignFromFirm(String firmName, UUID playerId);
    List<FirmEmployee> getCurrentEmployees(String firmName);
    List<Player> getOnlineEmployees(String firmName);
```

**business/structure/0003** `business/src/main/java/io/paradaux/business/services/impl/FirmServiceImpl.java:322-324`

```
if (url != null && !url.isBlank() && !url.matches("https://discord\\.gg/[A-Za-z0-9]{2,32}")) {
            throw new BadCommandException("Invalid Discord invite URL — must be https://discord.gg/<code>.");
        }
```

**business/structure/0005** `business/src/main/java/io/paradaux/business/services/impl/FirmStaffServiceImpl.java:254-254`

```
        List<FirmRolePermission> perms = roles.listPermissionsByRole(firmId, Objects.requireNonNull(roleName, "roleName must not be null").trim());
```

</details>

### business · plugin-architecture

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| business/plugin-architecture/0002 | major | high | unverified | `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:104-115` | Finance command handlers catch service-thrown exceptions and hand-format the error to a Message key, which the charter explicitly bans ("flag handlers that catch them to hand-format errors"). The hand… | Once the service throws semantic exceptions (finding #1), remove these try/catch blocks entirely and let the framework surface the exception. If distinct Message keys per failure are required, attach … | medium |
| business/plugin-architecture/0003 | major | high | unverified | `business/src/main/java/io/paradaux/business/commands/resolvers/OnlineFirmNameResolver.java:43-51` | ParameterResolver.suggestions() runs off the main thread (the code's own comment says 'Suggestions run on a Netty thread'), yet it reads live Bukkit state via Bukkit.getOnlinePlayers(). The charter fo… | Maintain the set of online-player UUIDs in a service (updated from PlayerJoin/PlayerQuit listeners on the main thread) and have the resolver union playerFirmNames over that cached UUID set, instead of… | small |
| business/plugin-architecture/0001 | major | high | confirmed | `business/src/main/java/io/paradaux/business/services/impl/FirmTransactionServiceImpl.java:74-87` | FirmTransactionServiceImpl signals all user-facing business/validation failures (invalid amount, insufficient funds, missing authorization/access) with raw JDK exceptions — IllegalArgumentException, I… | Throw the framework semantic exceptions from the service: BadCommandException for non-positive/invalid amounts, ExceedsLimitException (or a domain 'insufficient funds' semantic exception) for insuffic… | medium |
| business/plugin-architecture/0006 | minor | high | confirmed | `business/src/main/java/io/paradaux/business/commands/TaxCommands.java:84-91` | The taxInfo command handler performs economy arithmetic inline: it fetches the aggregate balance, looks up the weekly rate, and computes estimatedTax = totalBalance.multiply(rate).setScale(2, HALF_UP)… | Move the estimated-tax and rate-percentage computation into a service method (e.g. FirmBalanceTaxService.estimateWeeklyTax(firmId) returning an object with balance, rate, and tax), reusing the same ro… | small |
| business/plugin-architecture/0004 | minor | high | unverified | `business/src/main/java/io/paradaux/business/integration/RealtyRegionValidator.java:57-63` | RealtyRegionValidator.validate() calls Bukkit.getWorlds() (a Bukkit API call) and is reachable from an @Async command route: FirmCommands.setHq (@Async) -> FirmService.updateFirmHq -> FirmAreaShopServ… | Either snapshot the world list on the main thread and pass it in, or resolve HQ validation via the Realty service without iterating Bukkit.getWorlds() from the async path (e.g. accept a pre-resolved w… | small |
| business/plugin-architecture/0005 | minor | high | unverified | `business/src/main/java/io/paradaux/business/jobs/ExpireRequestsJob.java:26-34` | ExpireRequestsJob injects and calls FirmRequestMapper directly (field 'requests' is a FirmRequestMapper, constructed at lines 20-24). The charter states services are the ONLY layer that may call mappe… | Add an expireStale()/runExpiry() method to FirmRequestService (delegating to the mapper), inject FirmRequestService into ExpireRequestsJob instead of FirmRequestMapper, and call the service from run()… | small |
| business/plugin-architecture/0007 | nit | high | unverified | `business/src/main/java/io/paradaux/business/chat/FirmChatService.java:36-37` | The charter calls for an interface + impl split for every service. FirmChatService is a concrete class bound as a singleton with no service interface (unlike FirmService/FirmServiceImpl, FirmStaffServ… | If these are treated as services (they are injected into commands/resolvers), extract a FirmChatService interface with a FirmChatServiceImpl and bind it in BusinessModule for consistency; otherwise do… | small |

<details><summary>snippets</summary>

**business/plugin-architecture/0002** `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:104-115`

```
        try {
            audit.deposit(f.getFirmId(), sender.getUniqueId(), amount, memo);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.deposit.success", "firm", f.getDisplayName(), "amount", formatted);
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-personal");
        } catch (SecurityException e) {
            message.send(sender, "business.general.no-permission");
        }
```

**business/plugin-architecture/0003** `business/src/main/java/io/paradaux/business/commands/resolvers/OnlineFirmNameResolver.java:43-51`

```
    public List<String> suggestions(String prefix, CommandSender sender) {
        Set<String> pool = new LinkedHashSet<>();
        // Suggestions run on a Netty thread; snapshot the online players first so a
        // concurrent join/quit can't throw a ConcurrentModificationException mid-iteration.
        for (Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            pool.addAll(cache.playerFirmNames(online.getUniqueId()));
        }
        return Suggestions.match(pool, prefix, 20);
    }
```

**business/plugin-architecture/0001** `business/src/main/java/io/paradaux/business/services/impl/FirmTransactionServiceImpl.java:74-87`

```
if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        int businessAccountId = resolveAccountId(firmId);
        Account personal = treasury.resolveOrCreatePersonal(playerUuid);

        if (!treasury.hasFunds(personal.getAccountId(), amount)) {
            throw new IllegalStateException("Insufficient personal funds.");
        }

        if (!treasury.canAccessAccount(playerUuid, businessAccountId)) {
            throw new SecurityException("You don't have access to this business account.");
        }
```

**business/plugin-architecture/0006** `business/src/main/java/io/paradaux/business/commands/TaxCommands.java:84-91`

```
        boolean exempt = firmPropertyService.getBoolean(f.getFirmId(), EXEMPT_KEY).orElse(false);
        BigDecimal totalBalance = firmTransactionService.getAggregateBalance(f.getFirmId());
        BigDecimal rate = taxConfig.getWeeklyRate(totalBalance);
        BigDecimal estimatedTax = totalBalance.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        String balanceFmt = treasuryApi.formatAmount(totalBalance);
        String taxFmt = treasuryApi.formatAmount(estimatedTax);
        String ratePct = rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
```

**business/plugin-architecture/0004** `business/src/main/java/io/paradaux/business/integration/RealtyRegionValidator.java:57-63`

```
        try {
            for (World world : Bukkit.getWorlds()) {
                Object state = method.invoke(backend, regionId, world.getUID());
                if (state != null) {
                    return Optional.of(true);
                }
            }
```

**business/plugin-architecture/0005** `business/src/main/java/io/paradaux/business/jobs/ExpireRequestsJob.java:26-34`

```
    @Override
    public void run() {
        int transfers = requests.expireStaleTransfers();
        int invites = requests.expireStaleInvites();

        if (transfers > 0 || invites > 0) {
            log.info("[Business] Expired {} transfers and {} invites.", transfers, invites);
        }
    }
```

**business/plugin-architecture/0007** `business/src/main/java/io/paradaux/business/chat/FirmChatService.java:36-37`

```
@Singleton
public class FirmChatService {
```

</details>

### business · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| business/behaviour/0001 | minor | high | confirmed | `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:104-114` | The deposit and pay-into-firm command paths tell the player "insufficient personal funds" when the real cause is that the firm has no usable Treasury account. FirmTransactionServiceImpl.deposit()/payI… | In MiscCommands.doDeposit and MiscCommands.payInto, add a `catch (NoFirmAccountException e) { message.send(sender, "business.finance.no-account"); }` clause BEFORE the `catch (IllegalStateException)` … | trivial |
| business/behaviour/0002 | minor | medium | unverified | `business/src/main/java/io/paradaux/business/services/impl/FirmServiceImpl.java:224-263` | disbandFirm/adminDisbandFirm run @Async and are not serialized against each other. Both read the firm, check `archived`, then call disbandInternal, which snapshots the account list and calls firms.arc… | Guard the disband transition in Business: make FirmMapper.archiveFirm's UPDATE conditional (`WHERE firm_id = #{firmId} AND is_archived = 0`) and have disbandInternal short-circuit (return without drai… | small |

<details><summary>snippets</summary>

**business/behaviour/0001** `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:104-114`

```
        try {
            audit.deposit(f.getFirmId(), sender.getUniqueId(), amount, memo);
            String formatted = treasury.formatAmount(amount);
            message.send(sender, "business.finance.deposit.success", "firm", f.getDisplayName(), "amount", formatted);
        } catch (IllegalArgumentException e) {
            message.send(sender, "business.finance.invalid-amount");
        } catch (IllegalStateException e) {
            message.send(sender, "business.finance.insufficient-personal");
        } catch (SecurityException e) {
            message.send(sender, "business.general.no-permission");
        }
```

**business/behaviour/0002** `business/src/main/java/io/paradaux/business/services/impl/FirmServiceImpl.java:224-263`

```
    private void disbandInternal(Firm firm) {
        UUID proprietorUuid = UUID.fromString(firm.getProprietorUuid());

        // Snapshot the linked accounts and resolve the payout target before any
        // destructive step ...
        List<FirmAccount> firmAccountList = accounts.listAccountsByFirm(firm.getFirmId());
        Account personal = treasury.resolveOrCreatePersonal(proprietorUuid);

        // Mark disbanded first (auto-commits — this method isn't @Transactional)
        // so money only ever moves once the firm is durably archived.
        firms.archiveFirm(firm.getFirmId());
```

</details>

### business · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| business/testing/0002 | major | high | unverified | `business/src/main/java/io/paradaux/business/Business.java:128-128` | No startup-shaped test builds the Guice injector (HiberniaModule + BusinessModule + DatabaseModule) and calls registerAll() on CommandManager/ListenerManager (Business.java lines 121-140). A grep of t… | Add a wiring/smoke test that constructs the injector with the real modules (Treasury APIs mocked) and invokes CommandManager.registerAll() + ListenerManager.registerAll(), asserting no exception. If t… | medium |
| business/testing/0001 | minor | high | downgraded | `business/src/main/java/io/paradaux/business/chat/FirmChatService.java:37-37` | FirmChatService has zero tests (no test file references it anywhere under business/src/test). It owns real, privacy/moderation-critical logic that is trivially unit-testable without Bukkit statics: to… | Add a FirmChatServiceImplTest (plain Mockito) covering: toggle round-trips (add then remove returns false / clears the map entry), isSpyingOn honouring globalSpies over firmSpies and null firmId, isAn… | medium |
| business/testing/0004 | minor | high | unverified | `business/src/main/java/io/paradaux/business/commands/resolvers/FirmPlayerResolver.java:40-58` | The three custom ParameterResolvers have no tests (no test file references FirmPlayerResolver, FirmNameResolver, or OnlineFirmNameResolver). These are pure/injectable and directly testable, and FirmPl… | Add resolver unit tests with a mocked FirmPlayerService/FirmSuggestionCache: assert compact-32-char UUID resolves via findByUuid, an ambiguous 2-match prefix returns empty while a unique prefix resolv… | small |
| business/testing/0003 | minor | high | unverified | `business/src/test/java/io/paradaux/business/services/impl/FirmBalanceTaxServiceImplTest.java:162-189` | The test named withDriftFoldedIntoLargest does NOT exercise the drift-fold-into-largest branch it claims to cover. It uses equal balances 100/100/100 at rate 0.01, so each account's proportional tax i… | Add a case with unequal or non-evenly-divisible balances that produces a real remainder — e.g. balances 100/100/100 taxed at a total that doesn't divide by 3 (or two accounts 10.00 / 20.00 at a rate w… | small |

<details><summary>snippets</summary>

**business/testing/0002** `business/src/main/java/io/paradaux/business/Business.java:128-128`

```
        injector.getInstance(CommandManager.class).registerAll();
```

**business/testing/0001** `business/src/main/java/io/paradaux/business/chat/FirmChatService.java:37-37`

```
public class FirmChatService {
```

**business/testing/0004** `business/src/main/java/io/paradaux/business/commands/resolvers/FirmPlayerResolver.java:40-58`

```
    public Optional<FirmPlayer> resolve(String token, CommandSender sender) {
        if (token == null || token.isBlank()) return Optional.empty();

        // 1) UUID exact
        UUID asUuid = tryParseUuid(token);
        if (asUuid != null) {
            return players.findByUuid(asUuid);
        }
```

**business/testing/0003** `business/src/test/java/io/paradaux/business/services/impl/FirmBalanceTaxServiceImplTest.java:162-189`

```
    void multipleAccounts_splitProportionally_withDriftFoldedIntoLargest() {
        when(firmService.listAllActiveFirms()).thenReturn(List.of(firm(1)));
        when(firmAccountService.listAccountIds(1)).thenReturn(List.of(10, 11, 12));
        // Balances chosen so the proportional split leaves a rounding remainder.
        when(treasury.getBalancesByIds(List.of(10, 11, 12))).thenReturn(Map.of(
                10, new BigDecimal("100.00"), 11, new BigDecimal("100.00"), 12, new BigDecimal("100.00")));
        // total 300 * 0.01 = 3.00; per-account 1.00 each, no drift here but exercises the loop.
```

</details>

## Component: treasury-api-plugin  (mean 7.1)

### treasury-api-plugin · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-api-plugin/structure/0001 | major | high | confirmed | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:28-109` | UiAccessHandler is a command-layer handler (package commands/) that injects a persistence mapper (ExplorerUiMapper) directly and owns real business logic: role validation against VALID_ROLES, player-e… | Introduce an ExplorerUiService (interface + services/impl/ExplorerUiServiceImpl) that owns the mapper, the role-validation/link/keycloak orchestration and the @Transactional link-redemption sequence, … | medium |
| treasury-api-plugin/structure/0002 | minor | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:19-20` | The EXP_FMT DateTimeFormatter constant and the per-key status/expiry rendering (status = key.isRevoked() ? "Revoked" : "Active"; expiry = key.isRevoked() ? "—" : EXP_FMT.format(key.getExpiresAt())) ar… | Extract the formatter and the status/expiry derivation into one shared helper (e.g. a small ApiKeyView/formatting util or static methods) and call it from all three list-rendering loops. | small |
| treasury-api-plugin/structure/0003 | minor | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/model/economy/ApiKey.java:15-18` | keyType is a closed set (PERSONAL \| BUSINESS \| GOVERNMENT) modelled as a raw String, and the literals "PERSONAL"/"BUSINESS" are hand-typed in 8 sites across the service, both command handlers and pa… | Introduce a KeyType enum (PERSONAL, BUSINESS, GOVERNMENT) used by ApiKey.keyType, the service issue/list methods and the getKey type checks, with a String round-trip only at the MyBatis boundary. Elim… | small |
| treasury-api-plugin/structure/0004 | nit | medium | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/services/KeycloakAdminClient.java:27-28` | Service-layer placement is inconsistent: ApiKeyService uses an interface in services/ with the concrete class in services/impl/ (ApiKeyServiceImpl), while KeycloakAdminClient is a concrete service cla… | Pick one convention for this plugin: either give KeycloakAdminClient a services/ interface + services/impl/ concretion to match ApiKeyService, or accept concrete services in services/ and document it.… | trivial |

<details><summary>snippets</summary>

**treasury-api-plugin/structure/0001** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:28-109`

```
public class UiAccessHandler {

    private static final Set<String> VALID_ROLES = Set.of("admin", "government");

    private final ExplorerUiMapper mapper;
    private final KeycloakAdminClient keycloak;
```

**treasury-api-plugin/structure/0002** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:19-20`

```
    private static final DateTimeFormatter EXP_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault());
```

**treasury-api-plugin/structure/0003** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/model/economy/ApiKey.java:15-18`

```
    /** PERSONAL | BUSINESS. (The schema enum still permits GOVERNMENT for
     *  forward-compat with the migration baseline, but the issuance surface
     *  has been removed — no command path produces a GOVERNMENT row.) */
    private String keyType;
```

**treasury-api-plugin/structure/0004** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/services/KeycloakAdminClient.java:27-28`

```
@Singleton
public class KeycloakAdminClient {
```

</details>

### treasury-api-plugin · plugin-architecture

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-api-plugin/plugin-architecture/0001 | major | high | confirmed | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:32-138` | UiAccessHandler is a command-layer handler (delegated to from the @Route methods in TreasuryAPICommand for /treasuryapi ui ...) yet it injects ExplorerUiMapper directly and performs all persistence (r… | Introduce an ExplorerUiService (interface + impl) that owns ExplorerUiMapper and KeycloakAdminClient, performs role validation and the link/keycloak orchestration (throwing semantic exceptions on inva… | medium |
| treasury-api-plugin/plugin-architecture/0002 | major | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:59-61` | UiAccessHandler injects the Guice Injector and calls injector.getInstance(GroupReconciliationTask.class) as a service locator from business/command code, which the DI-discipline charter explicitly fla… | Replace the injected Injector + injector.getInstance(...) with an injected com.google.inject.Provider<GroupReconciliationTask> (or move self-sync behind a service that owns the optional dependency). R… | small |
| treasury-api-plugin/plugin-architecture/0005 | minor | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/BusinessKeyHandler.java:94-138` | Authorisation for reissue/revoke is enforced entirely in the command-layer handlers (canManage / getOwnerUuid checks), not in the service. ApiKeyService.reissueKey/revokeKey take only a keyId and perf… | Push the ownership/proprietorship authorisation into ApiKeyService (pass the acting UUID, throw NoPermissionException/NotFoundException from the service), so the invariant holds regardless of caller; … | medium |
| treasury-api-plugin/plugin-architecture/0004 | minor | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:53-54` | The list-entry status labels 'Revoked'/'Active' and the '—' expiry placeholder are hardcoded English player-facing strings built in the handler and passed into the {status}/{expiry} message placeholde… | Introduce Message keys for the status labels and the empty-expiry marker (e.g. treasuryapi.status.active / .revoked) and resolve them via Message instead of inline literals, in all three list loops. | small |
| treasury-api-plugin/plugin-architecture/0003 | minor | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:107-108` | The empty-roles fallback text 'player (no elevated roles)' is a hardcoded English player-facing string interpolated into the {roles} placeholder of the treasuryapi.ui.user.list message, rather than be… | Add a Message key (e.g. treasuryapi.ui.user.no-roles) and either send a distinct message when roles is empty or resolve the fallback via Message, so the string is localisable and consistent with the r… | trivial |

<details><summary>snippets</summary>

**treasury-api-plugin/plugin-architecture/0001** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:32-138`

```
    private final ExplorerUiMapper mapper;
...
        mapper.addRole(targetUuid, r, by);
...
        int removed = mapper.removeRole(target, r);
...
        List<String> roles = mapper.listRoles(target);
...
        String sub = mapper.findValidLinkSub(normalized);
        ...
        mapper.upsertIdentity(sub, sender.getUniqueId(), sender.getName(), "in-game:" + sender.getName());
        mapper.deleteLinkCode(normalized);
```

**treasury-api-plugin/plugin-architecture/0002** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:59-61`

```
        try {
            injector.getInstance(io.paradaux.treasuryapi.tasks.GroupReconciliationTask.class)
                    .reconcilePlayer(player.getUniqueId());
```

**treasury-api-plugin/plugin-architecture/0005** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/BusinessKeyHandler.java:94-138`

```
    public void doReissue(Player sender, int keyId) {
        ApiKey key = apiKeyService.getKey(keyId);
        if (key == null || !"BUSINESS".equals(key.getKeyType())) {
...
        if (!canManage(key, sender)) {
            message.send(sender, "treasuryapi.business.reissue.no-access");
            return;
        }
```

**treasury-api-plugin/plugin-architecture/0004** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:53-54`

```
            String status = key.isRevoked() ? "Revoked" : "Active";
            String expiry = key.isRevoked() ? "—" : EXP_FMT.format(key.getExpiresAt());
```

**treasury-api-plugin/plugin-architecture/0003** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:107-108`

```
        String display = roles.isEmpty() ? "player (no elevated roles)" : String.join(", ", roles);
        message.send(sender, "treasuryapi.ui.user.list", "player", playerName, "roles", display);
```

</details>

### treasury-api-plugin · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-api-plugin/behaviour/0001 | minor | medium | confirmed | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:111-133` | Link-code redemption is a non-atomic SELECT-then-DELETE: findValidLinkSub (a plain SELECT gated only on expires_at > NOW()) and the later deleteLinkCode are separate statements with no row lock, no DE… | Make redemption atomic: have deleteLinkCode return the affected-row count (or use a DELETE ... WHERE code=? AND expires_at>NOW() that also yields the sub, e.g. via a conditional DELETE guarded then SE… | small |
| treasury-api-plugin/behaviour/0002 | nit | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:135-138` | doUserRemove and doUserList resolve the target via resolveUuid, which calls Bukkit.getOfflinePlayer(name) with no hasPlayedBefore()/isOnline() guard — unlike doUserAdd (lines 79-83), which deliberatel… | For consistency with doUserAdd, apply the same hasPlayedBefore()/isOnline() guard in doUserRemove and doUserList (or in resolveUuid) and emit treasuryapi.ui.user.unknown-player when the name was never… | trivial |

<details><summary>snippets</summary>

**treasury-api-plugin/behaviour/0001** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:111-133`

```
public void doLink(Player sender, String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        String sub = mapper.findValidLinkSub(normalized);
        if (sub == null) {
            message.send(sender, "treasuryapi.ui.link.invalid");
            return;
        }

        mapper.upsertIdentity(sub, sender.getUniqueId(), sender.getName(), "in-game:" + sender.getName());
        mapper.deleteLinkCode(normalized);
```

**treasury-api-plugin/behaviour/0002** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:135-138`

```
    @SuppressWarnings("deprecation") // offline-safe name→UUID; blocking call runs on an async command thread
    private UUID resolveUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }
```

</details>

### treasury-api-plugin · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-api-plugin/testing/0002 | major | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/BusinessKeyHandler.java:131-138` | BusinessKeyHandler is untested. Its canManage gate encodes an explicit audit fix (ADT-111): reissue/revoke of a firm-scoped BUSINESS key must be gated on CURRENT proprietorship of the key's firm, not … | Add a BusinessKeyHandlerTest with a fake BusinessApi (firms().getFirm/isProprietor) and fake ApiKeyService: assert issue rejects non-proprietors and unknown firms, reissue/revoke reject a non-current-… | medium |
| treasury-api-plugin/testing/0001 | major | high | confirmed | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:63-99` | PersonalKeyHandler has zero tests, yet it contains security-relevant authorization logic: the owner-UUID equality gate on doReissue/doRevoke, the key-type discrimination (rejecting a BUSINESS key id p… | Add a PersonalKeyHandlerTest with a fake ApiKeyService and a stub Message recorder: assert doReissue/doRevoke send the no-access key when the sender is not the owner, the not-found key when getKey ret… | medium |
| treasury-api-plugin/testing/0003 | major | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/mappers/ApiKeyMapper.java:46-89` | None of the four mappers (ApiKeyMapper, ExplorerGroupMapper, ExplorerUiMapper, UuidBinaryTypeHandler) has any integration test, while sibling plugins treasury and business run their mappers against a … | Add a mapper integration test using the same MariaDB harness the sibling plugins already use (MariadbContainerExtension / the economy-flyway schema). Cover at minimum: reissue returns 0 rows for a rev… | large |
| treasury-api-plugin/testing/0004 | major | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/tasks/GroupReconciliationTask.java:80-91` | GroupReconciliationTask's only test covers the pure nodeKey() helper. The safety-critical mass-revoke guard in apply() — refuse to prune when the desired set is empty but current members exist (the do… | Extract the desired-vs-current diff-and-apply step (including the empty-desired guard) into a package-private method taking (int groupId, Set<UUID> desired) plus the injected ExplorerGroupMapper, and … | medium |
| treasury-api-plugin/testing/0005 | minor | high | unverified | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:70-91` | UiAccessHandler is untested and, unlike the other two handlers, is partly wired to hidden Bukkit statics (Bukkit.getOfflinePlayer in doUserAdd/resolveUuid, Bukkit.getPluginManager in doSelfSync). That… | Introduce a small injected PlayerResolver seam (name -> Optional<UUID>/played-before) so doUserAdd's unknown-player guard and doUserRemove/doUserList are unit-testable without Bukkit statics, then add… | medium |
| treasury-api-plugin/testing/0006 | minor | medium | confirmed | `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/services/KeycloakAdminClient.java:52-54` | KeycloakAdminClient has no tests. Two pieces are testable without a live Keycloak: the per-subject lock-stripe selection lockFor (ADT-113, which must be deterministic per sub and always in-bounds — Ma… | Make lockFor package-private (or add a stripeIndex(String) helper) and assert it returns the same in-range index for the same sub and handles a subject string whose hashCode is negative. If the secret… | small |
| treasury-api-plugin/testing/0007 | minor | high | unverified | `treasury-api-plugin/src/test/java/io/paradaux/treasuryapi/services/impl/ApiKeyServiceImplTest.java:110-124` | ApiKeyServiceImplTest is solid on the JWT mint and reissue-guard paths but leaves two service methods with no behavioural assertion: revokeKey (only ever wired as a fake returning 0 or 1, never assert… | Add assertions that revokeKey invokes mapper.revoke with the given keyId, and that listKeys/listBusinessKeysAccessibleByEmployee return exactly the mapper's list and forward the ownerUuid/keyType argu… | small |

<details><summary>snippets</summary>

**treasury-api-plugin/testing/0002** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/BusinessKeyHandler.java:131-138`

```
    private boolean canManage(ApiKey key, Player sender) {
        return key.getFirmId() != null
                && businessApi.firms().isProprietor(key.getFirmId(), sender.getUniqueId());
    }
```

**treasury-api-plugin/testing/0001** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:63-99`

```
    public void doReissue(Player sender, int keyId) {
        ApiKey key = apiKeyService.getKey(keyId);
        if (key == null || !"PERSONAL".equals(key.getKeyType())) {
            message.send(sender, "treasuryapi.personal.reissue.not-found");
            return;
        }
        if (!key.getOwnerUuid().equals(sender.getUniqueId())) {
            message.send(sender, "treasuryapi.personal.reissue.no-access");
```

**treasury-api-plugin/testing/0003** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/mappers/ApiKeyMapper.java:46-89`

```
    @Update("""
            UPDATE api_keys
               SET jwt_id     = #{jwtId},
                   issued_at  = #{issuedAt},
                   expires_at = #{expiresAt}
             WHERE key_id = #{keyId}
               AND revoked = 0
            """)
    int reissue(@Param("keyId") int keyId,
```

**treasury-api-plugin/testing/0004** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/tasks/GroupReconciliationTask.java:80-91`

```
    private void apply(int groupId, Set<UUID> desired) {
        Set<UUID> current = new HashSet<>(mapper.listLuckpermsMemberUuids(groupId));
        // Guard against a degraded/misconfigured LuckPerms resolving to zero members,
        // which would otherwise mass-revoke a whole group's synced access in one tick.
        if (desired.isEmpty() && !current.isEmpty()) {
            log.warning("Reconciliation of explorer group " + groupId
                    + " skipped removals: node resolved to zero members while "
```

**treasury-api-plugin/testing/0005** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:70-91`

```
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            message.send(sender, "treasuryapi.ui.user.unknown-player", "player", playerName);
            return;
        }
```

**treasury-api-plugin/testing/0006** `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/services/KeycloakAdminClient.java:52-54`

```
    private ReentrantLock lockFor(String sub) {
        return subjectLocks[Math.floorMod(sub.hashCode(), LOCK_STRIPES)];
    }
```

**treasury-api-plugin/testing/0007** `treasury-api-plugin/src/test/java/io/paradaux/treasuryapi/services/impl/ApiKeyServiceImplTest.java:110-124`

```
        assertThrows(IllegalStateException.class,
                () -> new ApiKeyServiceImpl(mapperReturning(active, 0), config).reissueKey(6));
    }

    /** In-memory mapper whose findById returns {@code found} and reissue affects {@code rows}. */
    private static ApiKeyMapper mapperReturning(ApiKey found, int rows) {
```

</details>

## Component: treasury-rest-api  (mean 7.6)

### treasury-rest-api · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-rest-api/structure/0002 | minor | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/ChestShopService.java:161-172` | The 1-based-page → int-offset conversion with overflow guard is duplicated verbatim between ChestShopService.validateOffset (lines 161-172) and TransactionService.getTransactions (lines 85-91). Both c… | Extract the offset-overflow computation and totalPages into one shared helper (e.g. a small Pagination utility in a common location for the module) and call it from both services so the bound and erro… | small |
| treasury-rest-api/structure/0003 | minor | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/WebhookService.java:129-134` | The byte[] → lowercase-hex encoding loop using Character.forDigit((b >> 4) & 0xF, 16)/(b & 0xF) is copy-pasted between WebhookService.newSecret (line 133) and HmacSha256.hex (lines 27-31). TransferSer… | Consolidate to a single hex helper (e.g. java.util.HexFormat.of() from the JDK, which returns lowercase hex directly) and call it from newSecret, HmacSha256.hex, and TransferService.sha256Hex. | small |
| treasury-rest-api/structure/0001 | minor | high | downgraded | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/util/DiscordWebhook.java:37-37` | MONEY is a shared static java.text.DecimalFormat, which is documented as NOT thread-safe. It is used from money() (line 84) via toPayload() (line 78), which the webhook dispatcher invokes for Discord … | Do not share a mutable DecimalFormat across threads. Either construct a fresh DecimalFormat inside money() per call, wrap it in a ThreadLocal<DecimalFormat>, or format with a thread-safe alternative (… | trivial |
| treasury-rest-api/structure/0004 | nit | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/FirmService.java:228-229` | Dangling orphan Javadoc comment at the end of FirmService with no method following it. The empty-to-null helper it once documented was moved to FirmFieldLimits.emptyToNull (ADT-120, referenced at line… | Delete the stray comment on lines 228-229. | trivial |

<details><summary>snippets</summary>

**treasury-rest-api/structure/0002** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/ChestShopService.java:161-172`

```
long offsetLong = (long) (page - 1) * limit;
        if (offsetLong > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAM",
                    "Query parameter 'page' is too large.");
        }
        return (int) offsetLong;
```

**treasury-rest-api/structure/0003** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/WebhookService.java:129-134`

```
private static String newSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : buf) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }
```

**treasury-rest-api/structure/0001** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/util/DiscordWebhook.java:37-37`

```
private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
```

**treasury-rest-api/structure/0004** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/FirmService.java:228-229`

```
    /** Converts a blank string to null so the DB column is set to NULL rather than empty string. */
}
```

</details>

### treasury-rest-api · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-rest-api/behaviour/0001 | minor | medium | confirmed | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/controller/AdminTransferController.java:36-47` | The admin transfer endpoint accepts an Idempotency-Key and TransferService.adminTransfer computes the same UNIQUE client_dedup_key (sha256Hex(fromAccountId + ':' + idempotencyKey)) as the token path, … | Wrap adminFirmService/adminTransfer invocation in the same withIdempotencyReplay(Supplier) retry-once pattern used by TransferController (extract it to a shared helper), or add a @ExceptionHandler(Dup… | small |
| treasury-rest-api/behaviour/0002 | minor | medium | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AdminFirmService.java:84-113` | disband() reads each firm account's balance via listFirmAccounts (a non-locking snapshot SELECT against account_balances_mat) and then sweeps that exact snapshot amount. executeTransfer re-acquires th… | Sweep the freshly-locked balance rather than the snapshot: within executeTransfer (or a dedicated sweep entry point) move min-of-full-locked-balance, i.e. read the FOR UPDATE balance and transfer that… | medium |

<details><summary>snippets</summary>

**treasury-rest-api/behaviour/0001** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/controller/AdminTransferController.java:36-47`

```
public ResponseEntity<TransferResponse> transfer(
            @AuthenticationPrincipal VerifiedToken verified,
            @Valid @RequestBody AdminTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ...
        TransferResponse response = transferService.adminTransfer(
                verified, request.fromAccountId(), request.toAccountId(), request.amount(), request.memo(), idempotencyKey);
        return ResponseEntity.ok(response);
```

**treasury-rest-api/behaviour/0002** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AdminFirmService.java:84-113`

```
        List<FirmAccountSummary> accounts = firmMapper.listFirmAccounts(firmId); // live links only
        ...
            BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
            ...
            if (balance.signum() > 0) {
                ...
                transferService.executeTransfer(accountId, proprietorPersonal, balance, DISBAND_MEMO,
                        proprietor, /* idempotencyKey */ null, /* bypassAuthRequired */ true);
                swept = balance.toPlainString();
                ...
            }
            accountMapper.archiveAccount(accountId);
```

</details>

### treasury-rest-api · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| treasury-rest-api/testing/0004 | major | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AccountService.java:59-103` | AccountService has no test (grep for getBalance / resolvePlayerAccount / existsGovernmentAccountByName / AMBIGUOUS_NAME in src/test returns nothing; the lone 'AccountService' match in AdminToolsIT is … | Add an EmbeddedDbIT for AccountService covering getBalance (found + 404) and resolvePlayerAccount for each branch, especially the government-name-collision AMBIGUOUS_NAME 409 (seed a player and a non-… | medium |
| treasury-rest-api/testing/0002 | major | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/TransferService.java:169-217` | Core transfer guard branches are untested. grep of src/test for SELF_TRANSFER, isArchived/archived, AUTHORIZATION_REQUIRED/requiresAuthorization, and the BUSINESS-key isFirmAccount ownership check (Tr… | Add tests: self-transfer rejected; archived source and archived destination each 404; an account with requires_authorization=1 as source or dest yields AUTHORIZATION_REQUIRED (and bypassAuthRequired=t… | medium |
| treasury-rest-api/testing/0003 | major | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/WebhookDispatcherService.java:206-274` | WebhookDispatcherService (293 lines) has no test (grep shows 0 test files reference it). This is the ledger-tailing delivery engine with non-trivial, pin-worthy logic that isn't reachable through the … | Add unit tests with stub mappers for: onFailure attempt-below-max → markRetry with the expected backoff (incl. retryMaxSeconds cap and the 1<<attempts shift), attempt-at-max → markFailed + incrementFa… | large |
| treasury-rest-api/testing/0008 | minor | medium | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/ratelimit/RateLimitOverrideService.java:36-53` | RateLimitOverrideService is only referenced in tests via a NOOP mapper embedded in RateLimitInterceptorTest (which asserts interceptor behaviour, not this class). Its own resolution logic is unverifie… | Add a unit test with a stub ApiRateLimitOverrideMapper: getMultiplier returns default for null owner, for absent override, and when findMultiplier throws; listAll returns empty when findAll throws; se… | small |
| treasury-rest-api/testing/0007 | minor | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AdminWebhookService.java:45-91` | Three SERVICE-scoped admin services have no tests: AdminWebhookService, AdminApiKeyService, and AdminRateLimitService (grep shows 0 test files for each). Their validation and authz guards are unverifi… | Add focused tests: AdminRateLimitService rejects multiplier<=0 and over-long note as INVALID_BODY; AdminWebhookService rejects over-64-char secret and non-public URL; AdminApiKeyService.revoke 404s on… | small |
| treasury-rest-api/testing/0005 | minor | high | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AuthService.java:43-112` | AuthService.rotateToken and adminForceRotate are untested (grep for rotateToken/adminForceRotate/RotateResponse in src/test finds only the ApiKeyMapper.rotateKey stub inside JwtTokenVerifierTest, whic… | Add tests: rotateToken on a live key returns a token that JwtTokenVerifier accepts and whose jti differs from the DB row it replaced; rotate on a revoked/missing key throws TOKEN_REVOKED/KEY_NOT_FOUND… | medium |
| treasury-rest-api/testing/0006 | minor | high | confirmed | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/FirmService.java:46-99` | Only 2 of FirmService's 8 public methods are tested. FirmUpdateValidationIT covers updateFirm and updateAccountDisplayName validation; getPublicFirm, getFirmBalance, getFirm, listAccounts, listEmploye… | Add EmbeddedDbIT coverage: getPublicFirm/getFirmBalance found + FIRM_NOT_FOUND; listRoles assembling permissions per role (incl. a role with no permissions → empty list); a non-BUSINESS token hitting … | medium |
| treasury-rest-api/testing/0009 | minor | medium | unverified | `treasury-rest-api/src/test/java/io/paradaux/treasuryrestapi/testsupport/EmbeddedMariaDb.java:113-286` | The integration-test schema is a hand-maintained DDL stand-in duplicated from economy-flyway rather than applying the authoritative Flyway migrations, and it deliberately omits the trg_postings_ai bal… | Prefer running the economy-flyway migrations against the embedded MariaDB (Flyway can execute DELIMITER-bearing migrations) so the test schema is the authoritative one, or add a CI check that diffs th… | medium |

<details><summary>snippets</summary>

**treasury-rest-api/testing/0004** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AccountService.java:59-103`

```
    public AccountByPlayerResponse resolvePlayerAccount(String uuid, String name) {
```

**treasury-rest-api/testing/0002** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/TransferService.java:169-217`

```
        // Step 4: no self-transfers
        if (fromAccountId == toAccountId) {
            log.warn("Transfer rejected: self-transfer attempted on accountId={}", fromAccountId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_TRANSFER",
```

**treasury-rest-api/testing/0003** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/WebhookDispatcherService.java:206-274`

```
    private void onFailure(DueDelivery d, Integer httpStatus, String error) {
        if (d.getAttempts() + 1 >= maxAttempts) {
            deliveryMapper.markFailed(d.getDeliveryId(), httpStatus, truncate(error));
            subscriptionMapper.incrementFailures(d.getSubscriptionId());
            int disabled = subscriptionMapper.disableIfOverThreshold(d.getSubscriptionId(), failureThreshold);
```

**treasury-rest-api/testing/0008** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/ratelimit/RateLimitOverrideService.java:36-53`

```
                .build(owner -> {
                    try {
                        BigDecimal m = mapper.findMultiplier(owner);
                        return m != null ? m : DEFAULT_MULTIPLIER;
                    } catch (RuntimeException e) {
                        ... return DEFAULT_MULTIPLIER;
```

**treasury-rest-api/testing/0007** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AdminWebhookService.java:45-91`

```
    @Transactional
    public long create(VerifiedToken verified, WebhookCreateRequest req) {
        AdminScope.require(verified);
        SsrfValidator.validate(req.targetUrl()); // https, public address, standard port
        validateSecret(req.secret());
```

**treasury-rest-api/testing/0005** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AuthService.java:43-112`

```
    @Transactional
    public RotateResponse rotateToken(VerifiedToken verified) {
```

**treasury-rest-api/testing/0006** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/FirmService.java:46-99`

```
    public PublicFirmResponse getPublicFirm(String displayName) {
        Firm firm = firmMapper.findFirmByDisplayName(displayName);
        if (firm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FIRM_NOT_FOUND", "Firm not found.");
```

**treasury-rest-api/testing/0009** `treasury-rest-api/src/test/java/io/paradaux/treasuryrestapi/testsupport/EmbeddedMariaDb.java:113-286`

```
    private static final String[] DDL = {
            // Minimal stand-ins for the shared economy tables the API reads/writes.
```

</details>

## Component: chestshop  (mean 7.1)

### chestshop · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| chestshop/structure/0002 | major | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/listeners/MarketListener.java:44-117` | Four classes in listeners/ (entrypoint layer) hold business-layer logic that is invoked directly by the service layer, and are injected into services as collaborators — inverting the stated one-direct… | Move the service-invoked methods into proper @Singleton services (e.g. MarketSyncService, StockCounterService, RestrictedSignService, SignBreakService) that the pipeline services inject; keep only the… | large |
| chestshop/structure/0001 | major | high | confirmed | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:89-140` | TransactionServiceImpl is a god class: 719 lines and 17 injected constructor dependencies (well above the ~10 charter threshold). It owns prepare/validate/execute/process end-to-end plus notification-… | Extract the cohesive sub-responsibilities into their own collaborators: the validation pipeline (rejectInvalidClientName/rejectCreativeMode/flagFreeShop/checkFundsAndStock/checkPermissions/checkStockF… | large |
| chestshop/structure/0003 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/EconomyServiceImpl.java:183-186` | EconomyService.canHold(UUID, BigDecimal) unconditionally returns true and ignores both parameters, yet it is used as a real guard at two decision points in PartialFillCalculator (lines 118 and 184: `i… | Either implement a genuine ceiling check (Treasury has no per-account cap in this economy, in which case say so) or remove canHold from EconomyService and delete the two dead `if (!economy.canHold(...… | small |
| chestshop/structure/0005 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/utils/NumberUtil.java:32-99` | NumberUtil (a vendored Acrobot util) carries five public static predicates that are never called anywhere in the component: isFloat, isDouble, isShort, isLong, and isEnchantment (0 usages each; only i… | Delete the unused isFloat/isDouble/isShort/isLong/isEnchantment methods (they are trivially reconstructable and vendored-fork edits are explicitly permitted here), leaving only the used helpers. | trivial |
| chestshop/structure/0004 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/utils/StringUtil.java:74-75` | Several static fields that are assigned once and never reassigned are declared as mutable `static` rather than `static final` constants — mutable static state the charter calls out. StringUtil.charact… | Declare these `private static final` (and for the int[] width table, treat it as immutable / consider an unmodifiable copy accessor). No behavioural change; removes the mutable-static footgun. | trivial |
| chestshop/structure/0007 | minor | medium | unverified | `chestshop/src/main/java/io/paradaux/chestshop/utils/encoding/Base64.java:40-48` | The vendored 1822-line Base64 implementation (iharder.net / Robert Harder) duplicates functionality the JDK has provided as java.util.Base64 since Java 8 (the module targets Java 21). Its full GZIP/en… | Since the legacy migration (migrateBlobEncoding, PAR-290) rewrites rows to plain java.util.Base64, retire the vendored class once migration is confirmed complete, or at minimum trim it to the single M… | medium |
| chestshop/structure/0006 | nit | high | confirmed | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:503-513` | The location-placeholder block (world/x/y/z with the ADT-140 null-world guard, plus price) is built twice with the same shape inside TransactionServiceImpl — once in sendShopLocationMessage (503-513) … | Extract a small private helper that populates the world/x/y/z (and price) placeholders from a Location into the replacement map/array, and call it from both message builders. | small |

<details><summary>snippets</summary>

**chestshop/structure/0002** `chestshop/src/main/java/io/paradaux/chestshop/listeners/MarketListener.java:44-117`

```
public class MarketListener implements Listener {
...
    // Invoked directly by TransactionService#process (was a @MONITOR Transaction listener).
    public void onTransaction(Transaction event) {
...
    // Invoked directly by ShopService#onCreated (was a @MONITOR CreatedShop listener).
    public void onShopCreated(CreatedShop event) {
```

**chestshop/structure/0001** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:89-140`

```
public class TransactionServiceImpl implements TransactionService {
...
    public TransactionServiceImpl(EconomyService economy, ShopService shops, AccountService accounts, SignBreakListener signBreak, StockCounterListener stockCounter, Message message, ItemService items, MarketListener market,
                              ChestShopConfiguration config, SignService signService, ShopBlockService shopBlockService, InventoryService inventoryService, AdminBypassService adminBypass, RestrictedSignListener restrictedSign, MetricsService metrics, PartialFillCalculator partialFill, GoodsTransfer goodsTransfer) {
```

**chestshop/structure/0003** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/EconomyServiceImpl.java:183-186`

```
    @Override
    public boolean canHold(UUID account, BigDecimal amount) {
        return true;
    }
```

**chestshop/structure/0005** `chestshop/src/main/java/io/paradaux/chestshop/utils/NumberUtil.java:32-99`

```
    public static boolean isFloat(String string) {
        try {
            Float.parseFloat(string);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
```

**chestshop/structure/0004** `chestshop/src/main/java/io/paradaux/chestshop/utils/StringUtil.java:74-75`

```
    private static String characters = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_'abcdefghijklmnopqrstuvwxyz{|}~...";
    private static int[] extraWidth = {4,2,5,6,...};
```

**chestshop/structure/0007** `chestshop/src/main/java/io/paradaux/chestshop/utils/encoding/Base64.java:40-48`

```
public class Base64 {

/* ********  P U B L I C   F I E L D S  ******** */


    /**
     * No options specified. Value is zero.
     */
    public final static int NO_OPTIONS = 0;
```

**chestshop/structure/0006** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:503-513`

```
    private void sendShopLocationMessage(PendingTransaction ctx, String key, String actorPlaceholder) {
        Location loc = ctx.getSign().getLocation();
        sendMessageToOwner(ctx.getOwnerAccount(), key, new String[]{
                "price", economy.format(ctx.getExactPrice()),
                actorPlaceholder, ctx.getClient().getName(),
                "world", loc.getWorld() != null ? loc.getWorld().getName() : "?", // ADT-140: world may be unloaded
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ())
        }, ctx.getStock());
```

</details>

### chestshop · plugin-architecture

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| chestshop/plugin-architecture/0007 | major | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/listeners/PhysicsBreakListener.java:18-21` | Listener registered on BlockPhysicsEvent (one of the charter's named hot events, fired for essentially every block-update in loaded chunks). The guard is not O(1)/allocation-free: handlePhysicsBreak(b… | Guard on the cheap Material check first (event.getBlock().getType() against Tag.SIGNS / a wall-sign set) before any getBlockData()/getState() call, so the vast majority of physics events return in O(1… | small |
| chestshop/plugin-architecture/0006 | minor | high | downgraded | `chestshop/src/main/java/io/paradaux/chestshop/commands/GiveCommand.java:43-98` | The @Route handler captures the whole tail as one greedy String, re-splits it into a token array, and hand-parses positional args: iterating indices, calling Bukkit.getPlayer(args[index]) and Integer.… | Model the signature with typed args (an optional player @Arg backed by a player ParameterResolver, an optional int quantity @Arg, and a greedy item-code arg) so the framework resolves player/quantity/… | medium |
| chestshop/plugin-architecture/0003 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/commands/ItemInfoCommand.java:69-72` | Command handler hand-formats an error to the sender with a hardcoded §-coloured string instead of a Message key, and catches the semantic failure to format it inline. Charter flags raw sender.sendMess… | Send the error via a Message key (e.g. message.send(sender, "chestshop.iteminfo_error")); keep the log.error. | trivial |
| chestshop/plugin-architecture/0002 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/commands/ShopInfoCommand.java:56-58` | Command handler emits a hardcoded, legacy-§-coloured error string via raw sender.sendMessage instead of a Message key. Charter: all player-facing text via Message keys; flag hardcoded strings, legacy … | Route through the already-injected Message bean with a key (e.g. message.send(sender, "chestshop.PLAYER_ONLY")). | trivial |
| chestshop/plugin-architecture/0001 | minor | high | confirmed | `chestshop/src/main/java/io/paradaux/chestshop/commands/VersionCommand.java:30-42` | Command handler emits player-facing text via raw sender.sendMessage(...) with hardcoded English strings and legacy section-code colours (Colours.* = §-codes) instead of Message keys. The charter requi… | Inject Message and send both lines via Message keys (e.g. message.send(sender, "chestshop.version", "name", ..., "version", ...) and "chestshop.config_reloaded"), removing the hardcoded strings and Co… | small |
| chestshop/plugin-architecture/0005 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/listeners/PlayerInteractListener.java:134-137` | Listener emits player-facing error text via raw player.sendMessage with a hardcoded §-coloured string instead of a Message key, mid-handler. The same listener otherwise uses message.send(...) with key… | Replace with message.send(player, "chestshop.sign_name_error") (or reuse an existing key); keep the log.error. | trivial |
| chestshop/plugin-architecture/0008 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/services/SignService.java:30-38` | SignService is a substantial injected @Singleton service (sign parsing, admin-shop/business detection, validity, item/owner/price/quantity extraction) but is a concrete class with no interface, unlike… | Extract a SignService interface and rename the class to SignServiceImpl (bind interface->impl in ChestShopModule), matching the plugin's consistent service split. | medium |
| chestshop/plugin-architecture/0004 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/InfoServiceImpl.java:202-204` | Service layer emits player-facing text via raw sender.sendMessage with a hardcoded §-coloured string (also at line 262: lines.getSender().sendMessage(Colours.GRAY + recipe.toString())). Charter forbid… | Send these through the injected Message bean with keys; for the recipe listing, add each recipe line as a Message value rather than a direct coloured sendMessage. | small |
| chestshop/plugin-architecture/0009 | nit | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/ChestShop.java:218-220` | ChestShop#registerEvent is a public manual-listener-registration helper with no callers anywhere in the component (all listeners are registered via HiberniaModule.listeners(...) + ListenerManager#regi… | Delete the unused registerEvent method so the only sanctioned registration path is ListenerManager. | trivial |

<details><summary>snippets</summary>

**chestshop/plugin-architecture/0007** `chestshop/src/main/java/io/paradaux/chestshop/listeners/PhysicsBreakListener.java:18-21`

```
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSign(BlockPhysicsEvent event) {
        signBreak.handlePhysicsBreak(event.getBlock());
    }
```

**chestshop/plugin-architecture/0006** `chestshop/src/main/java/io/paradaux/chestshop/commands/GiveCommand.java:43-98`

```
    public void give(@Sender CommandSender sender,
                     @GreedyArg(value = "args", sanitize = false) String argLine) {
        // The greedy arg captures the whole tail; split it back into the token
        // array the original variadic parser expects (whitespace-delimited).
        String[] args = argLine.trim().split("\\s+");
...
                Player target = Bukkit.getPlayer(args[index]);
...
                if (disregardedIndexes.contains(index) || !NumberUtil.isInteger(args[index]) || Integer.parseInt(args[index]) < 0) {
...
                quantity = Integer.parseInt(args[index]);
```

**chestshop/plugin-architecture/0003** `chestshop/src/main/java/io/paradaux/chestshop/commands/ItemInfoCommand.java:69-72`

```
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Colours.RED + "Error while generating shop sign name. Please contact an admin or take a look at the console/log!");
            log.error("Error while generating shop sign item name", e);
            return;
```

**chestshop/plugin-architecture/0002** `chestshop/src/main/java/io/paradaux/chestshop/commands/ShopInfoCommand.java:56-58`

```
        } else {
            sender.sendMessage(Colours.RED + "Command must be run by a player!");
        }
```

**chestshop/plugin-architecture/0001** `chestshop/src/main/java/io/paradaux/chestshop/commands/VersionCommand.java:30-42`

```
sender.sendMessage(Colours.GRAY + ChestShop.getPluginName() + "'s version is: " + Colours.GREEN + ChestShop.getVersion());
...
        sender.sendMessage(Colours.DARK_GREEN + "The config was reloaded.");
```

**chestshop/plugin-architecture/0005** `chestshop/src/main/java/io/paradaux/chestshop/listeners/PlayerInteractListener.java:134-137`

```
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(Colours.RED + "Error while generating shop sign item name. Please contact an admin or take a look at the console/log!");
                        log.error("Error while generating shop sign item name", e);
                        return;
```

**chestshop/plugin-architecture/0008** `chestshop/src/main/java/io/paradaux/chestshop/services/SignService.java:30-38`

```
@Singleton
public class SignService {

    private final ChestShopConfiguration config;

    @Inject
    public SignService(ChestShopConfiguration config) {
        this.config = config;
    }
```

**chestshop/plugin-architecture/0004** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/InfoServiceImpl.java:202-204`

```
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Colours.RED + "Error while generating full name. Please contact an admin or take a look at the console/log!");
            log.error("Error while generating full item name", e);
```

**chestshop/plugin-architecture/0009** `chestshop/src/main/java/io/paradaux/chestshop/ChestShop.java:218-220`

```
    public void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
```

</details>

### chestshop · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| chestshop/behaviour/0001 | major | high | confirmed | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/ShopServiceImpl.java:589-609` | refundOnRemoval credits the shop owner FIRST (economy.deposit, which is a SYSTEM→owner transfer, a mint) and only afterwards debits the server-economy account (debitServerEconomy → economy.withdraw). … | Reverse the order and gate on the mirror leg: first attempt debitServerEconomy (checking withdraw()'s boolean) and only economy.deposit() the owner if it succeeds (or if no server-economy account is c… | small |
| chestshop/behaviour/0003 | minor | medium | unverified | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/PartialFillCalculator.java:198-200` | getAmountOfAffordableItems does walletMoney.divide(pricePerItem, 0, FLOOR).intValueExact(). When the balance/price quotient exceeds Integer.MAX_VALUE, intValueExact() throws ArithmeticException, which… | Clamp the quotient before converting, e.g. use intValue() with a Math.min against a sane max (the shop's max amount / stack count) or catch the overflow and treat it as 'more than enough' by capping a… | small |
| chestshop/behaviour/0002 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/ShopServiceImpl.java:579-585` | chargeCreationFee correctly aborts if the player-side withdraw fails, but the mirrored creditServerEconomy(price) leg (SYSTEM→server-economy transfer) is unchecked: creditServerEconomy calls economy.d… | Give deposit() a success return and check the creditServerEconomy leg; on failure either compensate (refund the player) or log a reconciliation warning rather than reporting SHOP_FEE_PAID unconditiona… | small |
| chestshop/behaviour/0004 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:486-490` | In the SHOP_DEPOSIT_FAILED branch the owner is notified with the message key 'chestshop.CLIENT_DEPOSIT_FAILED' (the client-side failure text), not a shop/owner-appropriate key. The owner receives a me… | Send the owner an owner-oriented key (e.g. a dedicated 'your shop could not receive payment' message) rather than reusing chestshop.CLIENT_DEPOSIT_FAILED, so the notification names the correct side of… | trivial |

<details><summary>snippets</summary>

**chestshop/behaviour/0001** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/ShopServiceImpl.java:589-609`

```
        economy.deposit(account.getUuid(), refund, sign.getWorld());

        debitServerEconomy(refund, sign.getWorld());
        message.send(destroyer, "chestshop.SHOP_REFUNDED", "amount", economy.format(refund));
```

**chestshop/behaviour/0003** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/PartialFillCalculator.java:198-200`

```
    static int getAmountOfAffordableItems(BigDecimal walletMoney, BigDecimal pricePerItem) {
        return walletMoney.divide(pricePerItem, 0, RoundingMode.FLOOR).intValueExact();
    }
```

**chestshop/behaviour/0002** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/ShopServiceImpl.java:579-585`

```
        if (!economy.withdraw(player.getUniqueId(), price, player.getWorld())) {
            return false;
        }

        creditServerEconomy(price, player.getWorld());
        message.send(player, "chestshop.SHOP_FEE_PAID", "amount", economy.format(price));
        return true;
```

**chestshop/behaviour/0004** `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:486-490`

```
            case CLIENT_DEPOSIT_FAILED -> messageKey = "chestshop.CLIENT_DEPOSIT_FAILED";
            case SHOP_DEPOSIT_FAILED -> {
                sendMessageToOwner(ctx.getOwnerAccount(), "chestshop.CLIENT_DEPOSIT_FAILED", new String[0]);
                messageKey = "chestshop.SHOP_DEPOSIT_FAILED";
            }
```

</details>

### chestshop · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| chestshop/testing/0002 | major | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/ChestShop.java:144-156` | No startup-shaped test exists that builds the Guice module (ChestShopModule) and calls CommandManager.registerAll()/ListenerManager.registerAll(). Grep across chestshop/src/test/** for Guice.createInj… | Add a startup test that constructs the injector with ChestShopModule (over a MockBukkit server), resolves CommandManager and ListenerManager, and calls registerAll() on each, asserting no exception an… | medium |
| chestshop/testing/0003 | minor | high | unverified | `chestshop/src/main/java/io/paradaux/chestshop/mappers/typehandlers/UuidStringTypeHandler.java:41-43` | The two custom MyBatis TypeHandlers (UuidStringTypeHandler, DateLongTypeHandler) and the two mappers (AccountMapper with 10 SQL statements, ItemCodeMapper with 8) are entirely untested. All service te… | Add direct unit tests for UuidStringTypeHandler and DateLongTypeHandler (mock ResultSet/PreparedStatement, assert the string/long round-trip and null handling). Add an integration test that opens an i… | medium |
| chestshop/testing/0001 | minor | high | downgraded | `chestshop/src/test/java/io/paradaux/chestshop/services/impl/TransactionServiceImplTest.java:2016-2018` | The notification-cooldown regression test asserts the owner is re-notified after the cooldown lapses by sleeping 1100ms of wall-clock time against a 1-second config window. This is a real-time-depende… | Introduce an injectable time source (e.g. a LongSupplier or java.time.Clock constructor-injected into TransactionServiceImpl) and replace the two System.currentTimeMillis() reads at TransactionService… | medium |

<details><summary>snippets</summary>

**chestshop/testing/0002** `chestshop/src/main/java/io/paradaux/chestshop/ChestShop.java:144-156`

```
        injector.getInstance(io.paradaux.hibernia.framework.commander.CommandManager.class).registerAll();
...
        injector.getInstance(io.paradaux.hibernia.framework.events.ListenerManager.class).registerAll();
```

**chestshop/testing/0003** `chestshop/src/main/java/io/paradaux/chestshop/mappers/typehandlers/UuidStringTypeHandler.java:41-43`

```
    private static UUID toUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
```

**chestshop/testing/0001** `chestshop/src/test/java/io/paradaux/chestshop/services/impl/TransactionServiceImplTest.java:2016-2018`

```
            service.validate(pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5")));
            Thread.sleep(1100); // let the cooldown window lapse
```

</details>

## Component: economy-explorer  (mean 7.9)

### economy-explorer · structure

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| economy-explorer/structure/0001 | minor | high | confirmed | `app/transactions/page.tsx:181-189` | The searchParams-flattening helper (Record<string,string\|string[]\|undefined> -> Record<string,string>) is copy-pasted into 18 page modules under the names `flat` and `flatten`. The copies have drift… | Extract one shared helper (e.g. `flattenSearchParams` in lib/, colocated with the zod SP parsing) with a single agreed semantics for array-valued and empty-string params, and replace all 18 local defi… | small |
| economy-explorer/structure/0002 | minor | medium | unverified | `lib/sql/identity.ts:41-53` | Two `as never` casts on a write path suppress Kysely's Insertable/Updateable column-name and type checking for the explorer_identity insert/upsert. The explorer_identity table is fully typed in the DB… | Remove the `as never` casts and let the value objects type-check against the registry. If Kysely rejects them because a generated/default column (linked_at) is declared required, mark that column via … | small |

<details><summary>snippets</summary>

**economy-explorer/structure/0001** `app/transactions/page.tsx:181-189`

```
function flat(raw: Record<string, string | string[] | undefined>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(raw)) {
    if (Array.isArray(v)) {
      if (v.length > 0 && v[0] !== undefined) out[k] = v[0];
    } else if (v !== undefined) out[k] = v;
  }
  return out;
}
```

**economy-explorer/structure/0002** `lib/sql/identity.ts:41-53`

```
    .insertInto('explorer_identity')
    .values({
      keycloak_sub: args.sub,
      player_uuid_bin: uuidToBin(args.playerUuid),
      minecraft_name: args.minecraftName,
      linked_by: args.linkedBy,
    } as never)
    .onDuplicateKeyUpdate({
      player_uuid_bin: uuidToBin(args.playerUuid),
      minecraft_name: args.minecraftName ?? undefined,
    } as never)
```

</details>

### economy-explorer · behaviour

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| economy-explorer/behaviour/0001 | minor | high | confirmed | `components/PlayerDashboard.tsx:33-36` | Money stored as DECIMAL(19,2) is summed for display KPIs by re-parsing each string to a JS number (IEEE-754 double) and adding in JS, instead of summing in SQL as the rest of the DAL does. Individual … | Sum money in SQL (as done for businessWealth/getTotalSupply/getMoneyFlow) and pass the string total to fmtAmtFull, or sum the minor-unit integers (multiply cents) in JS and divide only at format time.… | medium |

<details><summary>snippets</summary>

**economy-explorer/behaviour/0001** `components/PlayerDashboard.tsx:33-36`

```
const totalBalance = accounts.reduce((s, a) => s + parseFloat(a.balance), 0);
  const income = trajectory.reduce((s, t) => s + parseFloat(t.credits), 0);
  const spend = trajectory.reduce((s, t) => s + parseFloat(t.debits), 0);
  const net = income - spend;
```

</details>

### economy-explorer · testing

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| economy-explorer/testing/0001 | major | high | confirmed | `lib/util/ssrf.ts:42-73` | lib/util/ssrf.ts is the SSRF guard for player-supplied webhook URLs (the first line of defence against a player pointing a webhook at 127.0.0.1, 169.254.169.254 cloud metadata, RFC1918, CGNAT, IPv6 lo… | Add a unit test that drives isBlockedAddress across each blocked range boundary (0.x, 10.x, 127.x, 169.254.x, 172.15/172.16/172.31/172.32, 192.168.x, 100.63/100.64/100.128, 224.x, ::1, ::, fe80::, fc0… | small |
| economy-explorer/testing/0005 | major | high | unverified | `test/integration/schema.sql:1-20` | The integration suite (test/integration/db.ts loads test/integration/schema.sql) runs the real lib/sql queries against a real MariaDB — good, no H2 dialect mismatch. But the DDL it runs is a 364-line … | Either run the integration tests against a Flyway-migrated database (apply economy-flyway migrations to the CI MariaDB service, drop the checked-in schema.sql), or add a drift check that diffs a mysql… | medium |
| economy-explorer/testing/0004 | minor | high | unverified | `lib/auth/requireRole.ts:17-22` | requireRole is the tier gate for /me, /admin/*, /government — a permission-edge behaviour the charter calls out for pinning tests. It is pure (only depends on the Viewer type and error classes) and tr… | Add a unit test matrix: anon throws UnauthorizedError; player meets 'player' but is rejected for 'government'/'admin' with ForbiddenError; government meets 'government' but not 'admin'; admin meets al… | small |
| economy-explorer/testing/0003 | minor | high | unverified | `lib/services/group.ts:95-101` | resolvePlayerUuid contains the only non-trivial pure logic in the group service — trimming, UUID-shape detection, and lower-casing a canonical UUID before falling through to a name lookup. It gates ad… | Add a unit test for resolvePlayerUuid: a mixed-case canonical UUID returns lower-cased verbatim without hitting the DAL; a non-UUID string is trimmed and delegated to findPlayerUuidByName (stub it); w… | small |
| economy-explorer/testing/0002 | minor | high | unverified | `lib/util/discord.ts:9-17` | isDiscordWebhookUrl is pure (explicitly documented as having no server-only deps) and mirrors the dispatcher's DiscordWebhook.java host/path allowlist — a classic drift-prone mirror pair. It has no te… | Add a small unit test asserting true for each allowlisted host with a valid /api/webhooks/<id>/<token> and /api/v10/webhooks/... path, and false for a non-Discord host, a bad path, and a non-URL strin… | trivial |
| economy-explorer/testing/0006 | minor | high | confirmed | `vitest.config.mts:15-20` | CI runs `npm run coverage` (vitest run --coverage) on every PR, but the coverage block declares no thresholds (grep for 'threshold\|thresholds\|lines\|branches' in vitest.config.mts returns nothing). … | Add a coverage.thresholds block (e.g. lines/functions at the current measured baseline) so the CI coverage step fails on regression and ratchets upward, matching the repo's stated coverage-gate conven… | trivial |
| economy-explorer/testing/0007 | nit | high | unverified | `lib/format.ts:31-46` | format.test.ts covers looksLikeUuid, accountLabel, shortenUuid, fmtN, fmtAmt, fmtAmtFull, fmtPct thoroughly, but fmtDate and fmtTs are exported and untested. fmtDate has a real behavioural branch — a … | Add cases for fmtDate('2026-05-01') vs fmtDate('2026-05-01T10:00:00Z') pinning the UTC-suffix branch, fmtTs(null) → '—' and fmtTs of a Date/string, and a fmtAmt test under a stubbed CURRENCY_SYMBOL='£… | trivial |

<details><summary>snippets</summary>

**economy-explorer/testing/0001** `lib/util/ssrf.ts:42-73`

```
export function isBlockedAddress(ip: string): boolean {
  const v = net.isIP(ip);
  if (v === 4) return isBlockedV4(ip);
  if (v === 6) return isBlockedV6(ip);
  return true;
}
```

**economy-explorer/testing/0005** `test/integration/schema.sql:1-20`

```
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE `accounts` (
  `account_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `account_type` enum('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NOT NULL,
```

**economy-explorer/testing/0004** `lib/auth/requireRole.ts:17-22`

```
export function requireRole(viewer: Viewer, min: RequiredRole): asserts viewer is Extract<Viewer, { anon: false }> {
  if (viewer.anon) throw new UnauthorizedError(`This page requires sign-in.`);
  if (RANK[viewer.role === 'player' ? 'player' : viewer.role] < RANK[min]) {
    throw new ForbiddenError(`This page requires ${min} access.`);
  }
}
```

**economy-explorer/testing/0003** `lib/services/group.ts:95-101`

```
export async function resolvePlayerUuid(input: string): Promise<string | null> {
  const trimmed = input.trim();
  if (UUID_RE.test(trimmed)) {
    return trimmed.toLowerCase();
  }
  return findPlayerUuidByName(trimmed);
}
```

**economy-explorer/testing/0002** `lib/util/discord.ts:9-17`

```
export function isDiscordWebhookUrl(raw: string): boolean {
  let u: URL;
  try {
    u = new URL(raw.trim());
  } catch {
    return false;
  }
  return HOSTS.has(u.hostname.toLowerCase()) && PATH.test(u.pathname);
}
```

**economy-explorer/testing/0006** `vitest.config.mts:15-20`

```
    coverage: {
      provider: 'v8',
      reporter: ['text', 'text-summary', 'html', 'lcov'],
      include: ['lib/**/*.ts'],
      exclude: ['lib/**/*.d.ts', 'lib/db.ts', 'lib/auth/authjs.ts'],
    },
```

**economy-explorer/testing/0007** `lib/format.ts:31-46`

```
export function fmtDate(d: string): string {
  return new Date(d.length === 10 ? d + 'T00:00:00Z' : d).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
}

export function fmtTs(ts: string | Date | null): string {
  if (!ts) return '—';
```

</details>

## Global dimensions

### global · build

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| global/build/0001 | minor | high | downgraded | `.github/workflows/treasury-rest-api-test.yml:63-66` | treasury-rest-api/build.gradle.kts (lines 96-111) wires a JaCoCo coverage ratchet (LINE COVEREDRATIO >= 0.55) into `check` via jacocoTestCoverageVerification, and the header comment says the money-han… | Add `:treasury-rest-api:jacocoTestReport :treasury-rest-api:jacocoTestCoverageVerification` (or `:treasury-rest-api:check`) to the Run tests step in treasury-rest-api-test.yml so the 0.55 ratchet actu… | trivial |
| global/build/0005 | minor | high | unverified | `.github/workflows/treasury-test.yml:57-66` | gradle/actions/wrapper-validation (which verifies the committed gradle-wrapper.jar against known-good checksums before it is executed) is applied inconsistently: the publish and docker workflows and c… | Add a `gradle/actions/wrapper-validation@v4` step (or a single reusable setup composite) to the six Gradle workflows that currently omit it, so every workflow that runs ./gradlew validates the wrapper… | trivial |
| global/build/0003 | minor | high | unverified | `build-logic/build.gradle.kts:22-23` | The Shadow plugin version 9.0.2 is hardcoded in two build files that must stay in lockstep: the root build.gradle.kts `id("com.gradleup.shadow") version "9.0.2" apply false` (line 14) and build-logic/… | Add a [plugins]/[versions] entry for shadow in libs.versions.toml and reference it from both the root build and build-logic (e.g. via libs.versions.shadow.get() for the build-logic classpath dep), so … | small |
| global/build/0004 | minor | high | unverified | `treasury/build.gradle.kts:192-215` | The publish-target repository block (snapshot/release URL selection + REPO_USER/REPO_PASS env credentials) that supplies the maven-publish repository for the api submodules is duplicated byte-for-byte… | Extract the publish-repository block into a shared convention plugin (e.g. io.paradaux.published-library-conventions) applied by treasury-api and business-api, so the paradaux.io snapshot/release repo… | small |
| global/build/0002 | minor | high | unverified | `treasury/treasury-api/build.gradle.kts:9-25` | The build-logic convention plugin io.paradaux.jvm-conventions owns exactly the Java toolchain (JavaLanguageVersion.of(21)) plus JavaCompile encoding=UTF-8 and release=21. Three JVM subprojects that do… | Apply id("io.paradaux.jvm-conventions") in treasury-api, business-api, and treasury-rest-api and delete the inline java.toolchain / JavaCompile.release blocks, leaving only the module-specific extras … | small |

<details><summary>snippets</summary>

**global/build/0001** `.github/workflows/treasury-rest-api-test.yml:63-66`

```
      - name: Run tests
        env:
          TREASURY_REST_TEST_JDBC_URL: jdbc:mariadb://127.0.0.1:3306/treasury_rest_test
          TREASURY_REST_TEST_DB_USER: root
          TREASURY_REST_TEST_DB_PASS: treasury
        run: ./gradlew --no-daemon :treasury-rest-api:test -Pci=true
```

**global/build/0005** `.github/workflows/treasury-test.yml:57-66`

```
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make Gradle wrapper executable
        run: chmod +x ./gradlew
```

**global/build/0003** `build-logic/build.gradle.kts:22-23`

```
    // root build's `plugins { … apply false }` block (com.gradleup.shadow 9.0.2).
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.0.2")
```

**global/build/0004** `treasury/build.gradle.kts:192-215`

```
subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

                    name = if (isSnapshot) "Snapshots" else "Releases"
                    url = uri(
                        if (isSnapshot)
                            "https://repo.paradaux.io/snapshots"
                        else
                            "https://repo.paradaux.io/releases"
                    )

                    credentials {
                        username = System.getenv("REPO_USER")
                        password = System.getenv("REPO_PASS")
                    }
                }
            }
        }
    }
}
```

**global/build/0002** `treasury/treasury-api/build.gradle.kts:9-25`

```
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar() // publish a -javadoc artifact for this documented public API (ADT no-javadoc-jar-published)
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}
```

</details>

### global · infra

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| global/infra/0001 | major | high | downgraded | `treasury-rest-api/Dockerfile:11-32` | The image runs with SPRING_PROFILES_ACTIVE=prod (Dockerfile line 26). The prod profile relocates the actuator management server to a separate port: application-prod.yaml lines 69-70 set `management.se… | Point the HEALTHCHECK at the management port: `curl -sf http://localhost:8081/actuator/health` (or 127.0.0.1:${MANAGEMENT_PORT:-8081}). Keep it aligned with the management.server.port used by the acti… | trivial |
| global/infra/0005 | minor | high | unverified | `.github/workflows/economy-explorer-schema-drift.yml:67-72` | Node version drift across the explorer's own toolchain. The Dockerfile builds on `node:22-alpine` (economy-explorer/Dockerfile lines 5/10/17) and economy-explorer-ci.yml uses `node-version: 22` (line … | Bump this workflow's `node-version` to 22 to match the Dockerfile and CI. Better, centralize the version via an `.nvmrc`/`.node-version` file and set `node-version-file:` in every setup-node step so i… | trivial |
| global/infra/0004 | minor | high | unverified | `.github/workflows/treasury-rest-api-docker.yml:99-111` | The Harbor push workflows use raw `docker build`/`docker push` rather than docker/build-push-action, and the image build/push steps are not pinned to action SHAs — but more concretely, the workflows p… | Switch to docker/build-push-action@<pinned-sha> with `provenance: true` and `sbom: true`, or add a buildx step emitting an SBOM attestation, so each immutable sha tag has verifiable provenance. Keep t… | small |

<details><summary>snippets</summary>

**global/infra/0001** `treasury-rest-api/Dockerfile:11-32`

```
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1
```

**global/infra/0005** `.github/workflows/economy-explorer-schema-drift.yml:67-72`

```
      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: economy-explorer/package-lock.json
```

**global/infra/0004** `.github/workflows/treasury-rest-api-docker.yml:99-111`

```
      - name: Build Docker image
        run: |
          IMAGE=${{ secrets.HARBOR_REGISTRY }}/paradaux-public/treasury-rest-api
          docker build \
            -t "${IMAGE}:${{ steps.tags.outputs.SHA_TAG }}" \
            -t "${IMAGE}:${{ steps.tags.outputs.MUTABLE_TAG }}" \
            treasury-rest-api
```

</details>

### global · dependencies

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| global/dependencies/0001 | minor | high | confirmed | `economy-explorer/package.json:27-27` | Production authentication for the economy-explorer is pinned to a pre-release beta of next-auth (declared ^5.0.0-beta.25, resolved 5.0.0-beta.31 in package-lock.json). next-auth v5 has been in beta fo… | Pin next-auth to an exact beta version (drop the caret: "5.0.0-beta.31") so beta bumps are deliberate, and track the v5 GA release to migrate off the beta line as soon as it ships. Do not upgrade blin… | small |
| global/dependencies/0002 | minor | medium | unverified | `economy-explorer/package.json:49-57` | The vitest/vite toolchain (vitest 2.1.9, vite 5.4.21, esbuild 0.21.5 resolved in package-lock.json) is a full major behind current (vitest 3.x, vite 6.x/7.x). esbuild 0.21.5 specifically is subject to… | Upgrade the test stack: vitest and @vitest/coverage-v8 to ^3.x (which pulls vite 6.x / esbuild >=0.25 and clears GHSA-67mh-4wv8-2f99). vitest 2->3 has minor config/API changes (workspace config, mock … | small |
| global/dependencies/0004 | minor | high | unverified | `gradle/libs.versions.toml:10-10` | The Hibernia Framework — the shared Paper foundation bundled (shaded) into every deployable plugin jar (treasury, business, treasury-api-plugin, chestshop) — is pinned in the version catalog to a movi… | Cut a fixed hibernia-framework release (e.g. 1.2.0) and pin the catalog to it before any release build, so a given monorepo commit shades a deterministic framework version. If snapshot iteration is ne… | medium |
| global/dependencies/0003 | minor | medium | unverified | `treasury-rest-api/build.gradle.kts:40-40` | mybatis-spring-boot-starter is pinned to 4.0.1 while the rest of the module targets Spring Boot 4.1.0 GA (root build.gradle.kts line 15, applied here via the org.springframework.boot plugin). The myba… | Confirm mybatis-spring-boot-starter 4.0.1 is officially supported on Spring Boot 4.1, and if a Spring-Boot-4-aligned MyBatis starter release exists, move to it; otherwise document the pin's Boot-4 com… | small |
| global/dependencies/0005 | nit | medium | unverified | `server/bot/package.json:1-13` | server/bot/package.json is an un-customised `npm init` stub: it declares no dependencies, points `main` at an index.js that does not exist in the directory (the directory contains only package.json), … | Either populate server/bot with its actual sources and dependencies, or remove the stub package.json if the bot is not a real component. If kept as a placeholder, add a description noting it is a stub… | trivial |

<details><summary>snippets</summary>

**global/dependencies/0001** `economy-explorer/package.json:27-27`

```
"next-auth": "^5.0.0-beta.25",
```

**global/dependencies/0002** `economy-explorer/package.json:49-57`

```
"@vitest/coverage-v8": "^2.1.8",
    "eslint": "^8.57.1",
    "eslint-config-next": "^15.1.0",
    "kysely-codegen": "^0.20.0",
    "tsx": "^4.19.0",
    "typescript": "^5.7.0",
    "vite-tsconfig-paths": "^5.1.4",
    "vitest": "^2.1.8",
    "wait-on": "^8.0.1"
```

**global/dependencies/0004** `gradle/libs.versions.toml:10-10`

```
hibernia = "1.2.0-SNAPSHOT"
```

**global/dependencies/0003** `treasury-rest-api/build.gradle.kts:40-40`

```
implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")
```

**global/dependencies/0005** `server/bot/package.json:1-13`

```
{
  "name": "bot",
  "version": "1.0.0",
  "description": "",
  "main": "index.js",
  "scripts": {
    "test": "echo \"Error: no test specified\" && exit 1"
  },
  "keywords": [],
  "author": "",
  "license": "ISC",
  "type": "commonjs"
}
```

</details>

### global · database

| id | sev | conf | verif | file:lines | description | recommendation | effort |
|---|---|---|---|---|---|---|---|
| global/database/0002 | minor | high | unverified | `economy-flyway/src/main/resources/db/migration/V1__initial.sql:367-385` | firm_sale is dead schema. V6's own header states it 'supersedes business-rian's stubbed firm_sale', and chestshop_sale replaced it. A full grep across all in-scope Java and TypeScript (treasury, busin… | Add a migration to DROP TABLE firm_sale (it has no inbound FKs and no code depends on it), removing both the dead table and the pointless V8 UNIQUE it carries. If retention of any legacy rows matters,… | small |
| global/database/0001 | minor | high | confirmed | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/mapper/LedgerMapper.java:60-69` | The REST API's paginated account transaction history filters on ledger_postings.account_id but orders by the *joined* ledger_txns.settlement_time DESC. No index supports this: ledger_postings has idx_… | Align the REST query with the Treasury one: ORDER BY lp.posting_id DESC (posting_id is auto-increment, monotonic with insert time, and covered by idx_postings_account so the sort is a backward index r… | trivial |
| global/database/0003 | minor | medium | unverified | `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/mapper/WebhookDeliveryMapper.java:31-42` | The webhook dispatcher's due-poll filters WHERE status='PENDING' AND next_attempt_at <= NOW() but orders by delivery_id ASC. The supporting index is idx_delivery_due(status, next_attempt_at) (V13) — i… | Either order by (next_attempt_at, delivery_id) so the existing idx_delivery_due leading columns satisfy the sort, or extend the index to idx_delivery_due(status, next_attempt_at, delivery_id) to make … | trivial |

<details><summary>snippets</summary>

**global/database/0002** `economy-flyway/src/main/resources/db/migration/V1__initial.sql:367-385`

```
CREATE TABLE firm_sale (
    sale_id        BIGINT       NOT NULL AUTO_INCREMENT,
    firm_id        INT          NOT NULL,
    occurred_at    DATETIME     NOT NULL,
    buyer_uuid_bin BINARY(16)   NULL,
    world          VARCHAR(32)  NOT NULL,
    x              INT          NOT NULL,
    y              INT          NOT NULL,
    z              INT          NOT NULL,
    item_id        VARCHAR(64)  NOT NULL,
    item_name      VARCHAR(128) NOT NULL,
    qty            INT          NOT NULL,
    price          DECIMAL(19,4) NOT NULL,
    source_msg_id  VARCHAR(128) NULL,
    PRIMARY KEY (sale_id),
```

**global/database/0001** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/mapper/LedgerMapper.java:60-69`

```
@Select("SELECT lp.posting_id, lp.txn_id, lp.amount, lp.memo, " +
            "       lt.message, lt.settlement_time, lt.initiator_uuid_bin, lt.plugin_system " +
            "FROM ledger_postings lp " +
            "JOIN ledger_txns lt ON lp.txn_id = lt.txn_id " +
            "WHERE lp.account_id = #{accountId} " +
            "ORDER BY lt.settlement_time DESC, lp.posting_id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
```

**global/database/0003** `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/mapper/WebhookDeliveryMapper.java:31-42`

```
    @Select("SELECT d.delivery_id AS deliveryId, d.subscription_id AS subscriptionId, d.txn_id AS txnId, "
            ...
            + "WHERE d.status = 'PENDING' AND d.next_attempt_at <= NOW() "
            + "ORDER BY d.delivery_id ASC LIMIT #{limit}")
```

</details>

## 4. Remediation plan

Work packages, blockers first, then majors clustered by theme. Sized by summed effort.

### WP1 — Majors: business · plugin-architecture (3 findings)

- **[business/plugin-architecture/0002]** (medium) `business/src/main/java/io/paradaux/business/commands/MiscCommands.java:104` — Finance command handlers catch service-thrown exceptions and hand-format the error to a Message key, which the charter explicitly bans ("flag handlers that catc
- **[business/plugin-architecture/0003]** (small) `business/src/main/java/io/paradaux/business/commands/resolvers/OnlineFirmNameResolver.java:43` — ParameterResolver.suggestions() runs off the main thread (the code's own comment says 'Suggestions run on a Netty thread'), yet it reads live Bukkit state via B
- **[business/plugin-architecture/0001]** (medium) `business/src/main/java/io/paradaux/business/services/impl/FirmTransactionServiceImpl.java:74` — FirmTransactionServiceImpl signals all user-facing business/validation failures (invalid amount, insufficient funds, missing authorization/access) with raw JDK 

### WP2 — Majors: business · testing (1 findings)

- **[business/testing/0002]** (medium) `business/src/main/java/io/paradaux/business/Business.java:128` — No startup-shaped test builds the Guice injector (HiberniaModule + BusinessModule + DatabaseModule) and calls registerAll() on CommandManager/ListenerManager (B

### WP3 — Majors: chestshop · behaviour (1 findings)

- **[chestshop/behaviour/0001]** (small) `chestshop/src/main/java/io/paradaux/chestshop/services/impl/ShopServiceImpl.java:589` — refundOnRemoval credits the shop owner FIRST (economy.deposit, which is a SYSTEM→owner transfer, a mint) and only afterwards debits the server-economy account (

### WP4 — Majors: chestshop · plugin-architecture (1 findings)

- **[chestshop/plugin-architecture/0007]** (small) `chestshop/src/main/java/io/paradaux/chestshop/listeners/PhysicsBreakListener.java:18` — Listener registered on BlockPhysicsEvent (one of the charter's named hot events, fired for essentially every block-update in loaded chunks). The guard is not O(

### WP5 — Majors: chestshop · structure (2 findings)

- **[chestshop/structure/0002]** (large) `chestshop/src/main/java/io/paradaux/chestshop/listeners/MarketListener.java:44` — Four classes in listeners/ (entrypoint layer) hold business-layer logic that is invoked directly by the service layer, and are injected into services as collabo
- **[chestshop/structure/0001]** (large) `chestshop/src/main/java/io/paradaux/chestshop/services/impl/TransactionServiceImpl.java:89` — TransactionServiceImpl is a god class: 719 lines and 17 injected constructor dependencies (well above the ~10 charter threshold). It owns prepare/validate/execu

### WP6 — Majors: chestshop · testing (1 findings)

- **[chestshop/testing/0002]** (medium) `chestshop/src/main/java/io/paradaux/chestshop/ChestShop.java:144` — No startup-shaped test exists that builds the Guice module (ChestShopModule) and calls CommandManager.registerAll()/ListenerManager.registerAll(). Grep across c

### WP7 — Majors: economy-explorer · testing (2 findings)

- **[economy-explorer/testing/0001]** (small) `lib/util/ssrf.ts:42` — lib/util/ssrf.ts is the SSRF guard for player-supplied webhook URLs (the first line of defence against a player pointing a webhook at 127.0.0.1, 169.254.169.254
- **[economy-explorer/testing/0005]** (medium) `test/integration/schema.sql:1` — The integration suite (test/integration/db.ts loads test/integration/schema.sql) runs the real lib/sql queries against a real MariaDB — good, no H2 dialect mism

### WP8 — Majors: global · infra (1 findings)

- **[global/infra/0001]** (trivial) `treasury-rest-api/Dockerfile:11` — The image runs with SPRING_PROFILES_ACTIVE=prod (Dockerfile line 26). The prod profile relocates the actuator management server to a separate port: application-

### WP9 — Majors: treasury · structure (1 findings)

- **[treasury/structure/0001]** (small) `treasury/src/main/java/io/paradaux/treasury/utils/Idempotency.java:1` — There are two classes with the identical fully-qualified name io.paradaux.treasury.utils.Idempotency — one in the plugin (treasury/src/main) and one in the publ

### WP10 — Majors: treasury · testing (2 findings)

- **[treasury/testing/0002]** (small) `treasury/src/main/java/io/paradaux/treasury/commands/resolvers/PayTargetResolver.java:57` — PayTargetResolver is the only custom ParameterResolver in the component and has no test (no *Resolver*Test.java exists under treasury/src/test). Its suggestions
- **[treasury/testing/0003]** (medium) `treasury/src/main/java/io/paradaux/treasury/services/cache/AccountRedirectCache.java:35` — The account-redirect chain is entirely untested despite being money-adjacent: AccountRedirectMapper (findAllRedirects/findRedirectAccountId/upsertRedirect) is r

### WP11 — Majors: treasury-api-plugin · plugin-architecture (2 findings)

- **[treasury-api-plugin/plugin-architecture/0001]** (medium) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:32` — UiAccessHandler is a command-layer handler (delegated to from the @Route methods in TreasuryAPICommand for /treasuryapi ui ...) yet it injects ExplorerUiMapper 
- **[treasury-api-plugin/plugin-architecture/0002]** (small) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:59` — UiAccessHandler injects the Guice Injector and calls injector.getInstance(GroupReconciliationTask.class) as a service locator from business/command code, which 

### WP12 — Majors: treasury-api-plugin · structure (1 findings)

- **[treasury-api-plugin/structure/0001]** (medium) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/UiAccessHandler.java:28` — UiAccessHandler is a command-layer handler (package commands/) that injects a persistence mapper (ExplorerUiMapper) directly and owns real business logic: role 

### WP13 — Majors: treasury-api-plugin · testing (4 findings)

- **[treasury-api-plugin/testing/0002]** (medium) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/BusinessKeyHandler.java:131` — BusinessKeyHandler is untested. Its canManage gate encodes an explicit audit fix (ADT-111): reissue/revoke of a firm-scoped BUSINESS key must be gated on CURREN
- **[treasury-api-plugin/testing/0001]** (medium) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/commands/PersonalKeyHandler.java:63` — PersonalKeyHandler has zero tests, yet it contains security-relevant authorization logic: the owner-UUID equality gate on doReissue/doRevoke, the key-type discr
- **[treasury-api-plugin/testing/0003]** (large) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/mappers/ApiKeyMapper.java:46` — None of the four mappers (ApiKeyMapper, ExplorerGroupMapper, ExplorerUiMapper, UuidBinaryTypeHandler) has any integration test, while sibling plugins treasury a
- **[treasury-api-plugin/testing/0004]** (medium) `treasury-api-plugin/src/main/java/io/paradaux/treasuryapi/tasks/GroupReconciliationTask.java:80` — GroupReconciliationTask's only test covers the pure nodeKey() helper. The safety-critical mass-revoke guard in apply() — refuse to prune when the desired set is

### WP14 — Majors: treasury-rest-api · testing (3 findings)

- **[treasury-rest-api/testing/0004]** (medium) `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/AccountService.java:59` — AccountService has no test (grep for getBalance / resolvePlayerAccount / existsGovernmentAccountByName / AMBIGUOUS_NAME in src/test returns nothing; the lone 'A
- **[treasury-rest-api/testing/0002]** (medium) `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/TransferService.java:169` — Core transfer guard branches are untested. grep of src/test for SELF_TRANSFER, isArchived/archived, AUTHORIZATION_REQUIRED/requiresAuthorization, and the BUSINE
- **[treasury-rest-api/testing/0003]** (large) `treasury-rest-api/src/main/java/io/paradaux/treasuryrestapi/service/WebhookDispatcherService.java:206` — WebhookDispatcherService (293 lines) has no test (grep shows 0 test files reference it). This is the ledger-tailing delivery engine with non-trivial, pin-worthy

### WP15 — Dependency upgrades (separate remediation run)

- **[global/dependencies/0001]** (small, minor) Production authentication for the economy-explorer is pinned to a pre-release beta of next-auth (declared ^5.0.0-beta.25, resolved 5.0.0-beta.31 in package-lock
- **[global/dependencies/0002]** (small, minor) The vitest/vite toolchain (vitest 2.1.9, vite 5.4.21, esbuild 0.21.5 resolved in package-lock.json) is a full major behind current (vitest 3.x, vite 6.x/7.x). e
- **[global/dependencies/0004]** (medium, minor) The Hibernia Framework — the shared Paper foundation bundled (shaded) into every deployable plugin jar (treasury, business, treasury-api-plugin, chestshop) — is
- **[global/dependencies/0003]** (small, minor) mybatis-spring-boot-starter is pinned to 4.0.1 while the rest of the module targets Spring Boot 4.1.0 GA (root build.gradle.kts line 15, applied here via the or
- **[global/dependencies/0005]** (trivial, nit) server/bot/package.json is an un-customised `npm init` stub: it declares no dependencies, points `main` at an index.js that does not exist in the directory (the

## 5. Audit coverage appendix

Rejected findings (failed verification): **1** (see `audit/rejected.jsonl`).

| component | dimension | files_examined | raw→kept | not_applicable |
|---|---|---|---|---|
| common | structure | 9 | 1→1 | TypeScript type-safety checks — no TS files in :common scope (Java-only library-jar) |
| common | behaviour | 9 | 1→1 | idempotency/retry dedup — no scheduled payouts or txn keys live in common; dedup is enforced in the consuming engines, i |
| common | testing | 10 | 4→4 | ParameterResolvers / custom resolver tests — common is a pure-JVM library with no Hibernia Framework resolvers, Hibernia |
| treasury | structure | 38 | 8→8 | TypeScript type-safety checks (any/as/non-null/tsconfig) — scope is pure Java (treasury plugin + treasury-api); no TS in |
| treasury | plugin-architecture | 29 | 6→6 | @Dialog/@Screen/@Action dialog handlers — treasury has no dialog UI (no @Screen/@Action/@Input in scope), reload() leavi |
| treasury | behaviour | 26 | 1→1 | api-website-consistency: verifying that the Next.js explorer and treasury-rest-api display the same numbers the plugin p |
| treasury | testing | 28 | 5→5 | H2-vs-production dialect mismatch: N/A — the harness uses real MariaDB (MariaDB4j/container) migrated by Flyway, no H2 p |
| business | structure | 30 | 5→5 | TypeScript type-safety checks (any/as/non-null assertions/tsconfig) — business is a pure Java Bukkit plugin, no TS in sc |
| business | plugin-architecture | 28 | 7→7 | Dialogs (@Screen/@Action/@Input/DialogFlow): the business plugin has no dialog handlers — all UI is chat commands, Hot e |
| business | behaviour | 29 | 2→2 | Inventory/GUI dupe vectors (charter item 5): business has no shop GUI, trade menu, or click-event item extraction — the  |
| business | testing | 44 | 4→4 | H2-vs-production dialect mismatch: N/A — mapper ITs use real MariaDB (MariaDB4j/external), not H2, Order-dependent / Thr |
| treasury-api-plugin | structure | 21 | 4→4 | TypeScript type-safety checks (any/as/non-null-assertion/tsconfig) — no TS in this component's scope, Currency double/fl |
| treasury-api-plugin | plugin-architecture | 18 | 5→5 | Resolvers: no ParameterResolver classes in this component, Events: no Listener/@EventHandler classes in this component,  |
| treasury-api-plugin | behaviour | 15 | 2→2 | conservation/mint-burn — component mints JWT credentials, never moves currency, double-spend — no account balances are r |
| treasury-api-plugin | testing | 18 | 7→7 | ParameterResolvers untested — none defined in treasury-api-plugin (no ParameterResolver implementations exist in scope), |
| treasury-rest-api | structure | 38 | 4→4 | TypeScript type-safety checks (any / non-null assertions / as casts / tsconfig strict): component is Spring Boot Java on |
| treasury-rest-api | behaviour | 24 | 2→2 | Dupe vectors (inventory/shop GUI click-event cancellation, shift-click, drag): treasury-rest-api has no inventory or Buk |
| treasury-rest-api | testing | 26 | 9→8 | HiberniaModule/registerAll startup-conflict test and ParameterResolver resolve/suggestion tests: this component is Sprin |
| chestshop | structure | 34 | 7→7 | TypeScript type-safety checks (any / non-null assertions / as casts / tsconfig strict flags) — chestshop is a pure Java  |
| chestshop | plugin-architecture | 42 | 9→9 | ParameterResolver charter checks: no ParameterResolver implementations exist in chestshop scope (CustomItemResolver is a |
| chestshop | behaviour | 18 | 4→4 | Scheduled payouts / vote rewards: chestshop has no scheduled or vote-reward payout code — trades are synchronous player- |
| chestshop | testing | 45 | 3→3 | H2-vs-production-dialect mismatch: not applicable — chestshop persists to SQLite (local files), not the shared MariaDB,  |
| economy-explorer | structure | 48 | 2→2 | Java null/Optional contracts, raw generics, unchecked casts, mutable static — component is TypeScript/Next.js, no Java i |
| economy-explorer | behaviour | 22 | 1→1 | Dupe vectors (inventory/shop-GUI click paths) — the viewer has no inventory or click-event handling; ChestShop money mov |
| economy-explorer | testing | 28 | 7→7 | ParameterResolvers / @Command route-conflict / HiberniaModule.registerAll startup test / messages.properties key cross-c |
| global | build | 22 | 5→5 | JS coverage-not-collected: economy-explorer-ci.yml runs `npm run coverage` (vitest --coverage) with typecheck+lint on a  |
| global | infra | 18 | 6→6 | Compose/runtime resource limits: no docker-compose or k8s manifests are within this cell's scope (server/ is local-dev o |
| global | dependencies | 18 | 5→5 | Network vulnerability scanners / npm audit / OWASP dependency-check — no network access in this environment; reasoned fr |
| global | database | 27 | 3→3 | realty mappers — realty is a git submodule, explicitly out of scope |
