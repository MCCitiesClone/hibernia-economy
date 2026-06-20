package io.paradaux.treasury.api.ingest;

import java.math.BigDecimal;

/**
 * Summary of a single {@link IngestApi#ingest} run.
 *
 * @param source             the source key the run was invoked with
 * @param filesScanned       count of input units examined (e.g. EssentialsX
 *                           userdata YAML files); includes skipped/failed
 * @param playersCreated     PERSONAL accounts newly created during the run
 * @param playersSkipped     entries that already had a Treasury PERSONAL
 *                           account (no transfer attempted — already migrated
 *                           or active player)
 * @param playersFailed      entries where parsing or transfer threw; the
 *                           ingester logged the underlying error
 * @param totalIngestedAmount sum of all balances transferred from the ingest
 *                           faucet to player PERSONAL accounts
 * @param durationMillis     wall-clock duration of the run
 */
public record IngestReport(
        String source,
        int filesScanned,
        int playersCreated,
        int playersSkipped,
        int playersFailed,
        BigDecimal totalIngestedAmount,
        long durationMillis
) {
    public IngestReport {
        if (totalIngestedAmount == null) {
            totalIngestedAmount = BigDecimal.ZERO;
        }
    }
}
