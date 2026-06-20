package io.paradaux.treasuryrestapi.dto;

/** Admin transfer between two arbitrary accounts. Amount is a decimal string. */
public record AdminTransferRequest(Long fromAccountId, Long toAccountId, String amount, String memo) {}
