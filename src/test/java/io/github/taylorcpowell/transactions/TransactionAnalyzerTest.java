package io.github.taylorcpowell.transactions;

import static io.github.taylorcpowell.transactions.TestTransactions.TIMESTAMP;
import static io.github.taylorcpowell.transactions.TestTransactions.accountIds;
import static io.github.taylorcpowell.transactions.TestTransactions.amount;
import static io.github.taylorcpowell.transactions.TestTransactions.credit;
import static io.github.taylorcpowell.transactions.TestTransactions.debit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TransactionAnalyzerTest {

    private final TransactionAnalyzer analyzer = new TransactionAnalyzer();

    @Test
    void producesTheExampleOutputForTheExampleInput() {
        List<Transaction> input = List.of(
                new Transaction("A100", "credit", amount("500"), "2026-05-10T10:00:00Z"),
                new Transaction("A100", "debit", amount("200"), "2026-05-10T12:00:00Z"),
                new Transaction("B200", "debit", amount("50"), "2026-05-10T11:00:00Z"),
                new Transaction("B200", "credit", amount("20"), "2026-05-10T13:00:00Z"),
                new Transaction("", "credit", amount("100"), "2026-05-10T14:00:00Z"));

        AnalysisResult result = analyzer.analyze(input);

        assertEquals(List.of(
                new AccountSummary("A100", amount("500"), amount("200"), amount("300"), amount("500"), 2, false),
                new AccountSummary("B200", amount("20"), amount("50"), amount("-30"), amount("50"), 2, true)),
                result.summaries());
        assertEquals("A100", result.topSpender());
    }

    @Test
    void returnsNoSummariesAndNoTopSpenderForEmptyInput() {
        AnalysisResult result = analyzer.analyze(List.of());

        assertEquals(List.of(), result.summaries());
        assertNull(result.topSpender());
    }

    @Test
    void rejectsANullList() {
        assertThrows(NullPointerException.class, () -> analyzer.analyze(null));
    }

    // Invalid transactions

    @Test
    void leavesInvalidTransactionsOutOfEveryTotalAndCount() {
        List<Transaction> input = Arrays.asList(
                credit("A100", "100"),
                new Transaction("A100", "refund", amount("999"), TIMESTAMP),
                new Transaction("A100", "debit", amount("-999"), TIMESTAMP),
                new Transaction("A100", "credit", null, TIMESTAMP),
                null,
                debit("A100", "40"));

        AnalysisResult result = analyzer.analyze(input);

        assertEquals(List.of(
                new AccountSummary("A100", amount("100"), amount("40"), amount("60"), amount("100"), 2, false)),
                result.summaries());
    }

    @Test
    void doesNotListAnAccountWhoseTransactionsAreAllInvalid() {
        List<Transaction> input = List.of(
                credit("A100", "10"),
                new Transaction("Z900", "refund", amount("10"), TIMESTAMP),
                credit("Z900", "0"));

        assertEquals(List.of("A100"), accountIds(analyzer.analyze(input)));
    }

    @Test
    void returnsAnEmptyResultWhenNoTransactionIsValid() {
        AnalysisResult result = analyzer.analyze(List.of(credit("", "10"), debit("A100", "-10")));

        assertEquals(List.of(), result.summaries());
        assertNull(result.topSpender());
    }

    @Test
    void missingOrMalformedTimestampsDoNotMakeATransactionInvalid() {
        List<Transaction> input = List.of(
                new Transaction("A100", "credit", amount("10"), null),
                new Transaction("A100", "debit", amount("3"), "yesterday"));

        AccountSummary summary = analyzer.analyze(input).summaries().get(0);

        assertEquals(2, summary.transactionCount());
        assertEquals(amount("7"), summary.endingBalance());
    }

    // Account IDs and ordering

    @Test
    void sortsSummariesByAccountIdInLexicographicOrder() {
        List<Transaction> input = List.of(
                credit("b1", "1"), credit("A2", "1"), credit("B1", "1"), credit("A10", "1"));

        // Plain String order: uppercase before lowercase, and "A10" before "A2".
        assertEquals(List.of("A10", "A2", "B1", "b1"), accountIds(analyzer.analyze(input)));
    }

    @Test
    void sortsManyAccountsByAccountIdWhateverTheInputOrder() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            ids.add("A" + i);
            ids.add("a" + i);
        }
        Collections.shuffle(ids, new Random(42));
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);

        AnalysisResult result = analyzer.analyze(ids.stream().map(id -> credit(id, "1")).toList());

        assertEquals(sorted, accountIds(result));
    }

    @Test
    void treatsAccountIdsAsCaseSensitive() {
        AnalysisResult result = analyzer.analyze(List.of(credit("A100", "10"), credit("a100", "5")));

        assertEquals(List.of("A100", "a100"), accountIds(result));
    }

    @Test
    void usesAccountIdsExactlyAsGivenSoSurroundingSpacesMakeADifferentAccount() {
        AnalysisResult result = analyzer.analyze(List.of(credit("A100", "10"), debit(" A100 ", "4")));

        assertEquals(List.of(" A100 ", "A100"), accountIds(result)); // a space sorts before letters
    }

    @Test
    void shuffledInputGivesTheSameResult() {
        List<Transaction> input = new ArrayList<>(List.of(
                credit("A100", "500"), debit("A100", "200"), credit("A100", "0.05"),
                debit("B200", "50"), credit("B200", "20"), debit("B200", "50.00"),
                debit("C300", "100"), credit("C300", "0.10")));
        AnalysisResult expected = analyzer.analyze(input);

        Random random = new Random(42); // a fixed seed, so any failure can be reproduced
        for (int attempt = 0; attempt < 25; attempt++) {
            Collections.shuffle(input, random);
            assertEquals(expected, analyzer.analyze(input), "input order: " + input);
        }
    }

    @Test
    void countsIdenticalTransactionsSeparatelyBecauseThereIsNoIdToDeduplicateOn() {
        AccountSummary summary = analyzer.analyze(List.of(credit("A100", "10"), credit("A100", "10")))
                .summaries().get(0);

        assertEquals(2, summary.transactionCount());
        assertEquals(amount("20"), summary.totalCredits());
    }

    // Totals

    @Test
    void addsDecimalAmountsExactly() {
        AccountSummary summary = analyzer.analyze(List.of(
                credit("A100", "0.1"), credit("A100", "0.2"), debit("A100", "0.3"))).summaries().get(0);

        assertEquals(amount("0.3"), summary.totalCredits()); // a double would give 0.30000000000000004
        assertEquals(amount("0"), summary.endingBalance());
    }

    @Test
    void handlesTotalsLargerThanALongCanHold() {
        AccountSummary summary = analyzer.analyze(List.of(
                credit("A100", "9223372036854775807"), credit("A100", "9223372036854775807"))).summaries().get(0);

        assertEquals(amount("18446744073709551614"), summary.totalCredits());
    }

    @Test
    void writesAmountsInTheirShortestExactForm() {
        AccountSummary summary = analyzer.analyze(List.of(credit("A100", "10.50"), debit("A100", "0.50")))
                .summaries().get(0);

        assertEquals(amount("10.5"), summary.totalCredits());
        assertEquals(amount("0.5"), summary.totalDebits());
        assertEquals(amount("10"), summary.endingBalance());
    }

    @Test
    void largestTransactionConsidersCreditsAndDebits() {
        AccountSummary summary = analyzer.analyze(List.of(
                credit("A100", "10"), debit("A100", "99.99"), credit("A100", "50"))).summaries().get(0);

        assertEquals(amount("99.99"), summary.largestTransaction());
    }

    // Overdrawn

    @Test
    void isOverdrawnWhenTheEndingBalanceIsBelowZero() {
        AccountSummary summary = analyzer.analyze(List.of(credit("A100", "10"), debit("A100", "10.01")))
                .summaries().get(0);

        assertEquals(amount("-0.01"), summary.endingBalance());
        assertTrue(summary.overdrawn());
    }

    @Test
    void isNotOverdrawnWhenTheEndingBalanceIsExactlyZero() {
        AccountSummary summary = analyzer.analyze(List.of(credit("A100", "25.00"), debit("A100", "25")))
                .summaries().get(0);

        assertEquals(amount("0"), summary.endingBalance());
        assertFalse(summary.overdrawn());
    }

    @Test
    void overdrawnDependsOnlyOnTheEndingBalanceNotOnTheBalanceAlongTheWay() {
        // The debit comes first, so the balance dips to -50 before the credit arrives.
        AccountSummary summary = analyzer.analyze(List.of(debit("A100", "50"), credit("A100", "80")))
                .summaries().get(0);

        assertFalse(summary.overdrawn());
    }

    // Top spender

    @Test
    void topSpenderIsTheAccountWithTheHighestTotalDebits() {
        AnalysisResult result = analyzer.analyze(List.of(
                debit("A100", "30"), debit("A100", "30"), debit("B200", "50"), credit("B200", "1000")));

        assertEquals("A100", result.topSpender()); // 60 in debits beats 50, whatever the credits
    }

    @Test
    void topSpenderComparesDebitsByValueNotByDigits() {
        // 9.5 is the digits 95 with one decimal place, so comparing digits would rank it above 10.
        AnalysisResult result = analyzer.analyze(List.of(debit("A100", "9.5"), debit("B200", "10")));

        assertEquals("B200", result.topSpender());
    }

    // The tie tests call topSpender directly with the larger ID first. Through analyze(), the
    // summaries arrive already sorted, so they would pass even without the tie-break.

    @Test
    void topSpenderTieGoesToTheLexicographicallySmallerAccountId() {
        List<AccountSummary> tied = List.of(withDebits("B200", "50"), withDebits("A100", "50"));

        assertEquals("A100", TransactionAnalyzer.topSpender(tied));
    }

    @Test
    void topSpenderTieIsDecidedByValueSo50And50Point00AreEqual() {
        List<AccountSummary> tied = List.of(withDebits("B200", "50.00"), withDebits("A100", "50"));

        assertEquals("A100", TransactionAnalyzer.topSpender(tied));
    }

    @Test
    void topSpenderIsTheSmallestAccountIdWhenNoAccountHasAnyDebits() {
        // Every account ties at zero debits, so the tie rule picks the smaller accountId.
        List<AccountSummary> noDebits = List.of(withDebits("B200", "0"), withDebits("A100", "0"));

        assertEquals("A100", TransactionAnalyzer.topSpender(noDebits));
    }

    @Test
    void ignoresAbsurdAmountsInsteadOfStallingOrCrashing() {
        AnalysisResult result = analyzer.analyze(List.of(
                credit("A100", "1e999999999"), credit("A100", "1e-10000"), credit("A100", "5")));

        assertEquals(List.of(
                new AccountSummary("A100", amount("5"), amount("0"), amount("5"), amount("5"), 1, false)),
                result.summaries());
    }

    // Results

    @Test
    void theResultKeepsItsOwnUnmodifiableCopyOfTheSummaries() {
        List<AccountSummary> summaries = new ArrayList<>(List.of(withDebits("A100", "5")));
        AnalysisResult result = new AnalysisResult(summaries, "A100");
        summaries.clear();

        assertEquals(1, result.summaries().size());
        assertThrows(UnsupportedOperationException.class, () -> result.summaries().clear());
    }

    /** A summary for an account with only debits, since topSpender looks only at debits and the ID. */
    private static AccountSummary withDebits(String accountId, String debits) {
        BigDecimal total = amount(debits);
        return new AccountSummary(accountId, BigDecimal.ZERO, total, total.negate(), total, 1, total.signum() > 0);
    }
}
