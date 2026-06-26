package io.paradaux.treasuryrestapi.controller;

import io.paradaux.treasuryrestapi.dto.AdminTransferRequest;
import io.paradaux.treasuryrestapi.dto.TransferResponse;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import io.paradaux.treasuryrestapi.service.TransferService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SERVICE-scoped admin transfer between arbitrary accounts (PAR-217). Hidden from
 * the OpenAPI doc; gated to SERVICE keys in {@link TransferService#adminTransfer}.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/admin/transfers")
public class AdminTransferController {

    private static final Logger log = LoggerFactory.getLogger(AdminTransferController.class);

    private final TransferService transferService;

    public AdminTransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @AuthenticationPrincipal VerifiedToken verified,
            @Valid @RequestBody AdminTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("POST /admin/transfers requested by keyId={} | from={} to={} amount={}",
                verified != null ? verified.keyId() : null,
                request.fromAccountId(), request.toAccountId(), request.amount());
        TransferResponse response = transferService.adminTransfer(
                verified, request.fromAccountId(), request.toAccountId(), request.amount(), request.memo(), idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
