package io.paradaux.treasury.services;

public interface DataExportService {

    /**
     * Exports all transactions for the given account as CSV,
     * uploads to Bytebin, and returns the resulting URL.
     */
    String exportTransactionsFor(int accountId);
}
