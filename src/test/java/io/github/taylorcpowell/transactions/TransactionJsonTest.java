package io.github.taylorcpowell.transactions;

import static io.github.taylorcpowell.transactions.TestTransactions.amount;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionJsonTest {

    private static final Transaction EMPTY = new Transaction(null, null, null, null);

    @Test
    void readsEveryFieldOfATransaction() throws IOException {
        List<Transaction> transactions = read("""
                [{ "accountId": "A100", "type": "credit", "amount": 500, "timestamp": "2026-05-10T10:00:00Z" }]""");

        assertEquals(List.of(new Transaction("A100", "credit", amount("500"), "2026-05-10T10:00:00Z")), transactions);
    }

    @Test
    void readsAmountsExactlyWithoutGoingThroughDouble() throws IOException {
        List<Transaction> transactions = read("""
                [{ "amount": 0.1000000000000000000001 }, { "amount": 123456789012345678901234567890 }]""");

        // As a double, the first would become 0.1 and the second 1.2345678901234568E29.
        assertEquals(amount("0.1000000000000000000001"), transactions.get(0).amount());
        assertEquals(amount("123456789012345678901234567890"), transactions.get(1).amount());
    }

    @Test
    void readsAnAmountWithAnExponentAsTheSameValue() throws IOException {
        BigDecimal amount = read("[{ \"amount\": 1e3 }]").get(0).amount();

        assertEquals(0, amount.compareTo(amount("1000")));
    }

    @Test
    void turnsFieldsOfTheWrongJsonTypeIntoNull() throws IOException {
        List<Transaction> transactions = read("""
                [{ "accountId": 100, "type": true, "amount": "10.00", "timestamp": 1715335200 }]""");

        assertEquals(List.of(EMPTY), transactions);
    }

    @Test
    void turnsMissingFieldsIntoNull() throws IOException {
        assertEquals(List.of(EMPTY), read("[{}]"));
    }

    @Test
    void turnsEntriesThatAreNotObjectsIntoEmptyTransactions() throws IOException {
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY), read("[null, 42, \"A100\", []]"));
    }

    @Test
    void ignoresFieldsItDoesNotUse() throws IOException {
        List<Transaction> transactions = read("""
                [{ "accountId": "A100", "type": "debit", "amount": 5, "currency": "USD", "memo": "coffee" }]""");

        assertEquals(List.of(new Transaction("A100", "debit", amount("5"), null)), transactions);
    }

    @Test
    void readsFilesSavedWithAByteOrderMarkAsWindowsToolsOftenDo() throws IOException {
        String json = "[{ \"accountId\": \"A100\", \"type\": \"credit\", \"amount\": 5 }]";
        byte[] utf8WithBom = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, json.getBytes(UTF_8));
        byte[] utf16LittleEndianWithBom = // what Windows PowerShell 5 writes with ">"
                concat(new byte[] {(byte) 0xFF, (byte) 0xFE}, json.getBytes(StandardCharsets.UTF_16LE));

        List<Transaction> expected = List.of(new Transaction("A100", "credit", amount("5"), null));
        assertEquals(expected, TransactionJson.readTransactions(new ByteArrayInputStream(utf8WithBom)));
        assertEquals(expected, TransactionJson.readTransactions(new ByteArrayInputStream(utf16LittleEndianWithBom)));
    }

    @Test
    void rejectsInputThatIsNotAnArray() {
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> read("{ \"accountId\": \"A100\" }"));

        assertEquals("expected a JSON array of transactions but found object", e.getMessage());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "   \n  "})
    void rejectsEmptyInput(String json) {
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> read(json));

        assertEquals("the input is empty; expected a JSON array of transactions", e.getMessage());
    }

    @Test
    void rejectsMalformedJsonAndSaysWhere() {
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> read("[{ \"accountId\": \"A100\", ]"));

        assertTrue(e.getMessage().startsWith("the input is not valid JSON at line 1, column "), e.getMessage());
    }

    @Test
    void rejectsInputThatEndsBeforeTheArrayIsClosedWithoutJacksonInternals() {
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> read("[{ \"accountId\": \"A100\" }"));

        // Jackson's own message goes on with "(start marker at [Source: REDACTED ...])".
        assertTrue(e.getMessage().endsWith("expected close marker for Array"), e.getMessage());
    }

    @Test
    void rejectsATransactionThatRepeatsAField() {
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> read("[{ \"accountId\": \"A100\", \"type\": \"credit\", \"amount\": 999, \"amount\": 5 }]"));

        assertTrue(e.getMessage().contains("Duplicate field 'amount'"), e.getMessage());
    }

    @Test
    void rejectsContentAfterTheArray() {
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> read("[] []"));

        // Jackson's own message goes on with "(bound as `com.fasterxml.jackson.databind.JsonNode`) ...".
        assertTrue(e.getMessage().endsWith("found after value"), e.getMessage());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"1e2147483649", "1e-2147483649"})
    void rejectsANumberTooBigOrSmallToRead(String amount) {
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> read("[{ \"amount\": " + amount + " }]"));

        assertEquals("the input has a number too big or small to read", e.getMessage());
    }

    @Test
    void rejectsANumberLongerThanJacksonAllowsWithoutNamingItsSettings() {
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> read("[{ \"amount\": 1" + "0".repeat(1000) + " }]"));

        // Jackson's own message ends "(1000, from `StreamReadConstraints.getMaxNumberLength()`)".
        assertTrue(e.getMessage().endsWith("exceeds the maximum allowed (1000)"), e.getMessage());
    }

    @Test
    void writesAnEmptyResultWithANullTopSpender() {
        String json = TransactionJson.writeResult(new AnalysisResult(List.of(), null));

        assertEquals("{\n  \"summaries\": [],\n  \"topSpender\": null\n}", json);
    }

    @Test
    void writesAmountsAsPlainNumbers() {
        AccountSummary summary = new AccountSummary(
                "A100", amount("1E+3"), amount("1E-7"), amount("999.9999999"), amount("1E+3"), 2, false);

        String json = TransactionJson.writeResult(new AnalysisResult(List.of(summary), "A100"));

        assertTrue(json.contains("\"totalCredits\": 1000,"), json);
        assertTrue(json.contains("\"totalDebits\": 0.0000001,"), json);
    }

    @Test
    void writesNonAsciiAccountIdsAsEscapesSoTheyArriveIntactOnAnyConsole() {
        AccountSummary summary = new AccountSummary(
                "Zo\u00EB", amount("5"), amount("0"), amount("5"), amount("5"), 1, false);

        String json = TransactionJson.writeResult(new AnalysisResult(List.of(summary), "Zo\u00EB"));

        assertTrue(json.contains("\"accountId\": \"Zo\\u00EB\""), json);
        assertTrue(json.chars().allMatch(c -> c < 128), "output should be plain ASCII: " + json);
    }

    private static List<Transaction> read(String json) throws IOException {
        return TransactionJson.readTransactions(new ByteArrayInputStream(json.getBytes(UTF_8)));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
