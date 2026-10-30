package io.github.taylorcpowell.transactions;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The rules by which transactions count. A transaction is valid when it has an account ID that
 * isn't empty or only whitespace, a type of exactly "credit" or "debit", and an amount greater
 * than zero with at most 30 digits on each side of the decimal point. The timestamp is not
 * checked, because no output depends on it.
 */
final class TransactionValidator {

    /**
     * No real amount needs more digits than this before or after the decimal point, not counting
     * zeros at the end of the decimal part. This stops input such as 1e999999999 from stalling
     * or crashing the run.
     */
    private static final int MAX_DIGITS = 30;

    /**
     * Matches an empty string, or one made only of whitespace by Java's or Unicode's definition.
     * String.isBlank() only uses Java's definition, which leaves out no-break spaces such as U+00A0.
     */
    private static final Pattern ONLY_WHITESPACE =
            Pattern.compile("[\\p{javaWhitespace}\\p{IsWhite_Space}]*");

    private TransactionValidator() {
    }

    /**
     * Checks one transaction against the rules.
     *
     * @return the transaction in validated form, or empty if it breaks a rule
     */
    static Optional<ValidTransaction> validate(Transaction transaction) {
        if (transaction == null) {
            return Optional.empty();
        }

        String accountId = transaction.accountId();
        if (accountId == null || ONLY_WHITESPACE.matcher(accountId).matches()) {
            return Optional.empty(); // missing, empty, or only whitespace
        }

        Optional<TransactionType> type = TransactionType.parse(transaction.type());
        if (type.isEmpty()) {
            return Optional.empty(); // missing, or not exactly credit or debit
        }

        BigDecimal amount = transaction.amount();
        if (amount == null || amount.signum() <= 0 || hasTooManyDigits(amount)) {
            return Optional.empty(); // missing, zero, negative, or absurdly long
        }

        return Optional.of(new ValidTransaction(accountId, type.get(), amount));
    }

    private static boolean hasTooManyDigits(BigDecimal amount) {
        // In int arithmetic this overflows for an amount like 1e2147483647, which would then pass.
        long digitsBeforePoint = (long) amount.precision() - amount.scale();
        // Checked first, because stripping the zeros of an amount like 100e2147483647 throws.
        if (digitsBeforePoint > MAX_DIGITS) {
            return true;
        }
        int digitsAfterPoint = amount.stripTrailingZeros().scale();
        return digitsAfterPoint > MAX_DIGITS;
    }

    /** A transaction that passed every rule. */
    record ValidTransaction(String accountId, TransactionType type, BigDecimal amount) {
    }
}
