package io.github.taylorcpowell.transactions;

import java.math.BigDecimal;

/**
 * Totals for one account, built from valid transactions. The components are declared in
 * the order of the example output, because Jackson writes them to JSON in declaration order.
 *
 * @param accountId          the account
 * @param totalCredits       the sum of its credit amounts
 * @param totalDebits        the sum of its debit amounts
 * @param endingBalance      total credits minus total debits, since every account starts at zero
 * @param largestTransaction the largest single amount, credit or debit
 * @param transactionCount   how many valid transactions the account has
 * @param overdrawn          whether the ending balance is below zero
 */
public record AccountSummary(
        String accountId,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal endingBalance,
        BigDecimal largestTransaction,
        int transactionCount,
        boolean overdrawn) {
}
