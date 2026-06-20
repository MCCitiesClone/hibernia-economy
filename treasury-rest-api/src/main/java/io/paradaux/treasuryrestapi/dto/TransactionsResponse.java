package io.paradaux.treasuryrestapi.dto;

import java.util.List;

public record TransactionsResponse(long accountId,
                                   int page,
                                   int totalPages,
                                   long totalItems,
                                   List<TransactionItem> items) {}
