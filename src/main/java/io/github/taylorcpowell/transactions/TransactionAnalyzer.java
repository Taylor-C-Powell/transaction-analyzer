package io.github.taylorcpowell.transactions;

import io.github.taylorcpowell.transactions.TransactionValidator.ValidTransaction;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a list of transactions into a per-account summary, labeling a top spender.
 *
 * <p>Every output is a sum, a maximum, or a count, so the order of the input never changes the
 * result and the transactions don't need sorting by timestamp. One pass over the
 * input, then one sort of the accounts: O(n + m log m) for n transactions across m
 * accounts. O(m) extra memory.
 */
public final class TransactionAnalyzer {

    /** Highest total debits first. During debit ties, the lexicographically smaller accountId first. */
    private static final Comparator<AccountSummary> SPENDING_ORDER =
            Comparator.comparing(AccountSummary::totalDebits, Comparator.reverseOrder())
                    .thenComparing(AccountSummary::accountId);

    /**
     * Analyzes the given transactions. Invalid ones are ignored completely and cause no changes.
     *
     * @param transactions in any order, with null elements counted as invalid
     * @return the sorted account summaries and the top spender
     */
    public AnalysisResult analyze(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");

        Map<String, AccountTotals> totalsByAccount = new HashMap<>();
        for (Transaction transaction : transactions) {
            TransactionValidator.validate(transaction).ifPresent(valid ->
                    totalsByAccount.computeIfAbsent(valid.accountId(), id -> new AccountTotals())
                            .add(valid));
        }

        List<AccountSummary> summaries = totalsByAccount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // by accountId, the order the output needs
                .map(entry -> entry.getValue().toSummary(entry.getKey()))
                .toList();
        return new AnalysisResult(summaries, topSpender(summaries));
    }

    /**
     * Picks the account with the highest total debits, breaking ties with the smaller accountId.
     * If no account has debits, every account ties at zero, so the smallest accountId wins.
     *
     * @param summaries the account summaries, in any order
     * @return the top spender, or null when there are no accounts at all
     */
    static String topSpender(List<AccountSummary> summaries) {
        return summaries.stream()
                .min(SPENDING_ORDER) // the account that comes first in spending order
                .map(AccountSummary::accountId)
                .orElse(null);
    }

    /** Running totals for one account while the input is read. */
    private static final class AccountTotals {
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal largestTransaction = BigDecimal.ZERO; // valid amounts are all above zero
        private int transactionCount;

        void add(ValidTransaction transaction) {
            BigDecimal amount = transaction.amount();
            switch (transaction.type()) {
                case CREDIT -> totalCredits = totalCredits.add(amount);
                case DEBIT -> totalDebits = totalDebits.add(amount);
            }
            largestTransaction = largestTransaction.max(amount);
            transactionCount++;
        }

        AccountSummary toSummary(String accountId) {
            BigDecimal endingBalance = totalCredits.subtract(totalDebits);
            boolean overdrawn = endingBalance.signum() < 0;
            return new AccountSummary(accountId, simplest(totalCredits), simplest(totalDebits),
                    simplest(endingBalance), simplest(largestTransaction), transactionCount, overdrawn);
        }
    }

    /**
     * Returns an amount in its shortest exact form, so 10.50 becomes 10.5. Equal
     * amounts then always look the same.
     */
    private static BigDecimal simplest(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped; // 1E+3 becomes 1000
    }
}
