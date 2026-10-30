package io.github.taylorcpowell.transactions;

import java.util.Optional;

/** The two kinds of transaction. A credit adds to the balance and a debit subtracts from it. */
enum TransactionType {
    CREDIT,
    DEBIT;

    /**
     * Parses a type from input. Only the exact values "credit" and "debit" are valid.
     *
     * @return the matching type, or empty for null or any other value
     */
    static Optional<TransactionType> parse(String value) {
        if ("credit".equals(value)) {
            return Optional.of(CREDIT);
        }
        if ("debit".equals(value)) {
            return Optional.of(DEBIT);
        }
        return Optional.empty();
    }
}
