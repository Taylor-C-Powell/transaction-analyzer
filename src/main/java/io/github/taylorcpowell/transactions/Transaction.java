package io.github.taylorcpowell.transactions;

import java.math.BigDecimal;

/**
 * One transaction as it arrived from input.
 *
 * <p>Input may be incorrectly formatted, so any component may be null or hold disallowed values.
 * The analyzer only uses a transaction after {@link TransactionValidator} accepts it.
 *
 * @param accountId the account the money moved into or out of
 * @param type      expected to be "credit" or "debit"
 * @param amount    expected to be greater than zero
 * @param timestamp when the transaction happened
 */
public record Transaction(String accountId, String type, BigDecimal amount, String timestamp) {
}
