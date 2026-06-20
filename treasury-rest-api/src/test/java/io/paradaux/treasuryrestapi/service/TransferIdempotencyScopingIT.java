package io.paradaux.treasuryrestapi.service;

import io.paradaux.treasuryrestapi.dto.TransferRequest;
import io.paradaux.treasuryrestapi.dto.TransferResponse;
import io.paradaux.treasuryrestapi.exception.ApiException;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import io.paradaux.treasuryrestapi.testsupport.EmbeddedDbIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The idempotency dedup key is derived from the source account + the header, not the
 * header alone. {@code ledger_txns.client_dedup_key} is globally UNIQUE, so a header-only
 * key lets one caller's {@code Idempotency-Key} collide with another's — blocking the
 * second caller (409) or, on a body match, leaking back a transaction that isn't theirs.
 *
 * <p>These tests prove the cross-account isolation while preserving the documented
 * single-caller replay / conflict semantics.
 */
class TransferIdempotencyScopingIT extends EmbeddedDbIT {

    @Autowired
    private TransferService transferService;

    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PAYEE   = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private VerifiedToken token(long accountId, UUID owner) {
        return new VerifiedToken(1L, owner, "PERSONAL", accountId, null);
    }

    @BeforeEach
    void seed() {
        insertAccount(1, "PERSONAL", OWNER_A, "A");   // caller A's account
        insertAccount(2, "PERSONAL", PAYEE,   "P");   // shared payee
        insertAccount(3, "PERSONAL", OWNER_B, "B");   // caller B's account
        seedBalance(1, "1000.00");
        seedBalance(2, "0.00");
        seedBalance(3, "1000.00");
    }

    @Test
    void sameAccountSameKeySameBody_replaysExistingTxn() {
        TransferResponse first = transferService.transfer(
                token(1, OWNER_A), new TransferRequest(null, 2L, "10.00", "memo"), "shared");
        TransferResponse replay = transferService.transfer(
                token(1, OWNER_A), new TransferRequest(null, 2L, "10.00", "memo"), "shared");

        assertThat(replay.txnId()).isEqualTo(first.txnId());
        // Debited exactly once.
        assertThat(sumPostings(1)).isEqualByComparingTo("-10.00");
        assertThat(rowCount("ledger_txns")).isEqualTo(1);
    }

    @Test
    void sameAccountSameKeyDifferentBody_conflicts() {
        transferService.transfer(token(1, OWNER_A), new TransferRequest(null, 2L, "10.00", null), "k");

        ApiException ex = catchThrowableOfType(ApiException.class, () -> transferService.transfer(
                token(1, OWNER_A), new TransferRequest(null, 2L, "20.00", null), "k"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void differentAccountsReusingSameKey_doNotCollide() {
        // Account 1 posts a 10.00 transfer keyed "dup".
        TransferResponse a = transferService.transfer(
                token(1, OWNER_A), new TransferRequest(null, 2L, "10.00", null), "dup");

        // Account 3 reuses the SAME "dup" key for a DIFFERENT (20.00) transfer.
        // Pre-fix this collided with A's globally-unique key and 409'd; now it succeeds.
        TransferResponse b = transferService.transfer(
                token(3, OWNER_B), new TransferRequest(null, 2L, "20.00", null), "dup");

        assertThat(b.txnId()).isNotEqualTo(a.txnId());
        assertThat(sumPostings(1)).isEqualByComparingTo("-10.00");
        assertThat(sumPostings(3)).isEqualByComparingTo("-20.00");
        assertThat(rowCount("ledger_txns")).isEqualTo(2);
    }
}
