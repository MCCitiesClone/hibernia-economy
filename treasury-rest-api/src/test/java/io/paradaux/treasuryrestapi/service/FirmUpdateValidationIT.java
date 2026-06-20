package io.paradaux.treasuryrestapi.service;

import io.paradaux.treasuryrestapi.dto.FirmUpdateRequest;
import io.paradaux.treasuryrestapi.exception.ApiException;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import io.paradaux.treasuryrestapi.testsupport.EmbeddedDbIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Firm/account string updates are bounded to their column widths before reaching the DB,
 * so an over-long value is a clean 400 INVALID_BODY rather than a driver-level
 * data-truncation 500. Validation runs ahead of the firm/account lookups, so these reject
 * paths need no seeded firm.
 */
class FirmUpdateValidationIT extends EmbeddedDbIT {

    @Autowired
    private FirmService firmService;

    private static final UUID OWNER = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private VerifiedToken businessToken() {
        return new VerifiedToken(1L, OWNER, "BUSINESS", null, 1L);
    }

    private void assertInvalidBody(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        ApiException ex = catchThrowableOfType(ApiException.class, call);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_BODY");
    }

    @Test
    void overlongDiscordUrl_rejected() {
        assertInvalidBody(() -> firmService.updateFirm(
                businessToken(), new FirmUpdateRequest("x".repeat(256), null)));
    }

    @Test
    void overlongHqRegion_rejected() {
        assertInvalidBody(() -> firmService.updateFirm(
                businessToken(), new FirmUpdateRequest(null, "y".repeat(65))));
    }

    @Test
    void overlongAccountDisplayName_rejected() {
        assertInvalidBody(() -> firmService.updateAccountDisplayName(
                businessToken(), 5L, "z".repeat(256)));
    }

    @Test
    void blankAccountDisplayName_rejected() {
        assertInvalidBody(() -> firmService.updateAccountDisplayName(businessToken(), 5L, "   "));
    }

    @Test
    void emptyUpdateBody_rejected() {
        assertInvalidBody(() -> firmService.updateFirm(
                businessToken(), new FirmUpdateRequest(null, null)));
    }

    @Test
    void personalKeyCannotUpdateFirm() {
        VerifiedToken personal = new VerifiedToken(1L, OWNER, "PERSONAL", 1L, null);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> firmService.updateFirm(personal, new FirmUpdateRequest("https://discord.gg/ok", null)));
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo("FORBIDDEN");
    }
}
