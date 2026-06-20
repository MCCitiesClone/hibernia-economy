package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/**
 * Forward cursor "pull" feed of settled transactions for an account.
 *
 * @param accountId  the account the feed is scoped to
 * @param cursor     the {@code since} cursor the caller supplied
 * @param nextCursor the {@code txn_id} of the last item — pass this as {@code since}
 *                   on the next call. {@code null} when no items were returned
 *                   (caller is caught up; keep the previous cursor).
 * @param hasMore    {@code true} when the page was full (more may be available now)
 * @param items      transactions with {@code txn_id > cursor}, oldest-first
 */
public record TransactionFeedResponse(long accountId,
                                      long cursor,
                                      Long nextCursor,
                                      boolean hasMore,
                                      List<TransactionItem> items) {}
