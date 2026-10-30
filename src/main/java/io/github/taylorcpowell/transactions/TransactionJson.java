package io.github.taylorcpowell.transactions;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads transactions from JSON and writes results as JSON. This is the only class that handles
 * anything about JSON or Jackson.
 */
final class TransactionJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            // Read decimals straight into BigDecimal, never through double, so no digit is lost.
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            // A field given twice is ambiguous.
            // The whole input is rejected, not just that transaction.
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            // Reject content after the array, such as a second array or stray text.
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            // Write 1000 rather than 1E+3.
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            // Escape characters outside ASCII.
            .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
            .build();

    // The layout of the example output.
    private static final ObjectWriter WRITER = MAPPER.writer(new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"))
            .withSeparators(Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                    .withArrayEmptySeparator("")));

    private TransactionJson() {
    }

    /**
     * Reads a JSON array of transactions. A problem inside one transaction, such as a missing
     * field, a field of the wrong JSON type, or an entry that isn't an object, doesn't stop the
     * read. The affected fields become null, and the validator decides later whether the
     * transaction still counts. A field given twice, or a number Jackson can't read, stops the
     * read with an error.
     *
     * @throws InvalidInputException if the input is empty, isn't valid JSON, or isn't an array
     * @throws IOException           if the input can't be read
     */
    static List<Transaction> readTransactions(InputStream json) throws IOException {
        JsonNode root = parse(json);
        if (root.isMissingNode()) {
            throw new InvalidInputException("the input is empty; expected a JSON array of transactions");
        }
        if (!root.isArray()) {
            String found = root.getNodeType().toString().toLowerCase(Locale.ROOT);
            throw new InvalidInputException("expected a JSON array of transactions but found " + found);
        }

        List<Transaction> transactions = new ArrayList<>(root.size());
        for (JsonNode entry : root) {
            transactions.add(new Transaction(
                    textOrNull(entry.get("accountId")),
                    textOrNull(entry.get("type")),
                    numberOrNull(entry.get("amount")),
                    textOrNull(entry.get("timestamp"))));
        }
        return transactions;
    }

    /** Writes a result using the same fields, order, and layout as the example output. */
    static String writeResult(AnalysisResult result) {
        try {
            return WRITER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            // A result holds only strings, numbers, and booleans, so reaching this means a bug.
            throw new IllegalStateException("could not write the result as JSON", e);
        }
    }

    private static JsonNode parse(InputStream json) throws IOException {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            JsonLocation at = e.getLocation();
            String where = at == null ? "" : " at line " + at.getLineNr() + ", column " + at.getColumnNr();
            // Some of Jackson's messages end with details of its own internals, such as
            // "(start marker at [Source: REDACTED ...])", or name one of its settings, such as
            // "from `StreamReadConstraints.getMaxNumberLength()`". This removes those details.
            String reason = String.valueOf(e.getOriginalMessage())
                    .replaceFirst("(?s) \\((start marker at|bound as) .*", "")
                    .replaceFirst(", from `[^`]*`", "");
            throw new InvalidInputException("the input is not valid JSON" + where + ": " + reason, e);
        } catch (NumberFormatException e) {
            // Jackson can't turn an exponent such as 1e2147483649 into a BigDecimal at all.
            // Without this catch the run would crash.
            throw new InvalidInputException("the input has a number too big or small to read", e);
        }
    }

    /** Only a JSON string counts as text. A number or boolean in a text field is treated as missing. */
    private static String textOrNull(JsonNode field) {
        return field != null && field.isTextual() ? field.textValue() : null;
    }

    /** Only a JSON number counts as an amount. A string such as "10.00" is treated as missing. */
    private static BigDecimal numberOrNull(JsonNode field) {
        return field != null && field.isNumber() ? field.decimalValue() : null;
    }
}
