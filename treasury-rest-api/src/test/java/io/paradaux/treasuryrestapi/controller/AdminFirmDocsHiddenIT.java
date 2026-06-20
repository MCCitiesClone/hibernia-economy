package io.paradaux.treasuryrestapi.controller;

import io.paradaux.treasuryrestapi.testsupport.EmbeddedDbIT;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the admin firm endpoints are intentionally undocumented: {@code @Hidden}
 * keeps {@code /api/v1/admin/firms/**} out of the OpenAPI doc (and Swagger UI),
 * while the public surface is still published.
 */
class AdminFirmDocsHiddenIT extends EmbeddedDbIT {

    @Test
    void adminFirmEndpointsAreNotInTheOpenApiDocument() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // hidden admin surface absent…
                .andExpect(content().string(not(containsString("/api/v1/admin/firms"))))
                .andExpect(content().string(not(containsString("/admin/firms/{firmId}/disband"))))
                .andExpect(content().string(not(containsString("/api/v1/admin/accounts"))))
                .andExpect(content().string(not(containsString("/api/v1/admin/transfers"))))
                // …but the public surface is still documented (sanity check the doc isn't empty).
                .andExpect(content().string(containsString("/api/v1/transfers")));
    }
}
