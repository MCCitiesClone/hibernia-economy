package io.paradaux.treasuryrestapi.service;

import io.paradaux.treasuryrestapi.dto.TransactionsResponse;
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
 * Pagination math must not overflow. {@code (page - 1) * limit} in {@code int} wraps to a
 * negative OFFSET for large pages (e.g. page 21474840 at limit 100), which MariaDB rejects
 * — surfacing as a 500. The offset is now computed in {@code long} and out-of-range pages
 * are a clean 400.
 */
class TransactionPaginationValidationIT extends EmbeddedDbIT {

    @Autowired
    private TransactionService transactionService;

    private static final UUID OWNER = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private VerifiedToken token() {
        return new VerifiedToken(1L, OWNER, "PERSONAL", 1L, null);
    }

    @BeforeEach
    void seed() {
        insertAccount(1, "PERSONAL", OWNER, "acct");
    }

    private ApiException reject(int page, int limit) {
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> transactionService.getTransactions(token(), 1, page, limit));
        assertThat(ex).as("page=%d limit=%d should be rejected", page, limit).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_PARAM");
        return ex;
    }

    @Test
    void maxIntPage_rejectedAsInvalidParam_not500() {
        reject(Integer.MAX_VALUE, 100);
    }

    @Test
    void smallestOverflowingPage_rejected() {
        // (21474840 - 1) * 100 overflows a signed int into a negative offset.
        reject(21474840, 100);
    }

    @Test
    void pageBelowOne_stillRejected() {
        reject(0, 20);
        reject(-1, 20);
    }

    @Test
    void limitOutOfRange_stillRejected() {
        reject(1, 0);
        reject(1, 101);
    }

    @Test
    void largeButServablePage_returnsEmptyPageWithoutError() {
        // offset = (1000 - 1) * 100 = 99_900 — fits int, just past the (empty) data.
        TransactionsResponse resp = transactionService.getTransactions(token(), 1, 1000, 100);
        assertThat(resp.items()).isEmpty();
        assertThat(resp.totalItems()).isZero();
        assertThat(resp.accountId()).isEqualTo(1L);
    }
}
