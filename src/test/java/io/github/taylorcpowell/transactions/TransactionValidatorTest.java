package io.github.taylorcpowell.transactions;

import static io.github.taylorcpowell.transactions.TestTransactions.TIMESTAMP;
import static io.github.taylorcpowell.transactions.TestTransactions.amount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.taylorcpowell.transactions.TransactionValidator.ValidTransaction;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionValidatorTest {

    @Test
    void acceptsACompleteTransaction() {
        Optional<ValidTransaction> valid = TransactionValidator.validate(
                new Transaction("A100", "credit", amount("500"), TIMESTAMP));

        assertEquals(Optional.of(new ValidTransaction("A100", TransactionType.CREDIT, amount("500"))), valid);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("transactionsThatBreakARule")
    void rejectsTransactionsThatBreakARule(String description, Transaction transaction) {
        assertTrue(TransactionValidator.validate(transaction).isEmpty());
    }

    static Stream<Arguments> transactionsThatBreakARule() {
        return Stream.of(
                arguments("a null transaction", null),
                arguments("a missing accountId", new Transaction(null, "credit", amount("10"), TIMESTAMP)),
                arguments("an empty accountId", new Transaction("", "credit", amount("10"), TIMESTAMP)),
                arguments("a whitespace-only accountId", new Transaction(" \t ", "credit", amount("10"), TIMESTAMP)),
                arguments("an accountId of no-break spaces", new Transaction("\u00A0\u2007\u202F", "credit", amount("10"), TIMESTAMP)),
                arguments("an accountId of other Unicode whitespace", new Transaction("\u0085\u3000", "credit", amount("10"), TIMESTAMP)),
                arguments("a missing type", new Transaction("A100", null, amount("10"), TIMESTAMP)),
                arguments("an empty type", new Transaction("A100", "", amount("10"), TIMESTAMP)),
                arguments("an unknown type", new Transaction("A100", "refund", amount("10"), TIMESTAMP)),
                arguments("a capitalized type", new Transaction("A100", "Credit", amount("10"), TIMESTAMP)),
                arguments("a type with spaces around it", new Transaction("A100", " debit ", amount("10"), TIMESTAMP)),
                arguments("a missing amount", new Transaction("A100", "credit", null, TIMESTAMP)),
                arguments("a zero amount", new Transaction("A100", "credit", amount("0"), TIMESTAMP)),
                arguments("a zero amount with decimals", new Transaction("A100", "debit", amount("0.00"), TIMESTAMP)),
                arguments("a negative amount", new Transaction("A100", "debit", amount("-5"), TIMESTAMP)),
                arguments("an amount with 31 digits", new Transaction("A100", "credit", amount("1e30"), TIMESTAMP)),
                arguments("an amount with 31 decimal places", new Transaction("A100", "credit", amount("1e-31"), TIMESTAMP)),
                arguments("an amount of 1e999999999", new Transaction("A100", "credit", amount("1e999999999"), TIMESTAMP)),
                arguments("an amount of 1e2147483647, whose digit count overflows an int", new Transaction("A100", "credit", amount("1e2147483647"), TIMESTAMP)),
                arguments("an amount of 100e2147483647, whose scale overflows if its zeros are stripped", new Transaction("A100", "credit", amount("100e2147483647"), TIMESTAMP)));
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({"credit, CREDIT", "debit, DEBIT"})
    void acceptsExactlyCreditAndDebit(String type, TransactionType expected) {
        Optional<ValidTransaction> valid = TransactionValidator.validate(
                new Transaction("A100", type, amount("10"), TIMESTAMP));

        assertEquals(expected, valid.orElseThrow().type());
    }

    @Test
    void keepsTheAccountIdExactlyAsGiven() {
        Optional<ValidTransaction> valid = TransactionValidator.validate(
                new Transaction(" a100 ", "credit", amount("10"), TIMESTAMP));

        assertEquals(" a100 ", valid.orElseThrow().accountId());
    }

    @ParameterizedTest(name = "amount {0}")
    @ValueSource(strings = {
        "123456789012345678901234567890",   // 30 digits
        "0.123456789012345678901234567891", // 30 decimal places, none of them trailing zeros
        "1.00000000000000000000000000000000000" // trailing zeros don't count
    })
    void acceptsAmountsWithUpToThirtyDigitsOnEachSideOfThePoint(String value) {
        assertTrue(TransactionValidator.validate(new Transaction("A100", "credit", amount(value), TIMESTAMP)).isPresent());
    }

    @ParameterizedTest(name = "timestamp [{0}]")
    @CsvSource(value = {"NULL", "''", "not a timestamp", "2026-13-45T99:00:00Z"}, nullValues = "NULL")
    void acceptsAnyTimestampBecauseTheRulesDoNotCheckIt(String timestamp) {
        Optional<ValidTransaction> valid = TransactionValidator.validate(
                new Transaction("A100", "credit", amount("10"), timestamp));

        assertTrue(valid.isPresent());
    }
}
