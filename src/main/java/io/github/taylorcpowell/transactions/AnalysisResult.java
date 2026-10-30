package io.github.taylorcpowell.transactions;

import java.util.List;

/**
 * What the analyzer returns.
 *
 * @param summaries  one summary per account with at least one valid transaction, sorted by
 *                   accountId
 * @param topSpender the account with the highest total debits, with ties going to the
 *                   lexicographically smaller accountId, or null when there are no accounts
 */
public record AnalysisResult(List<AccountSummary> summaries, String topSpender) {

    public AnalysisResult {
        summaries = List.copyOf(summaries); // unmodifiable
    }
}
