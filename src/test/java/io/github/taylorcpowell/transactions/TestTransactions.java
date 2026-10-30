package io.github.taylorcpowell.transactions;

import java.math.BigDecimal;
import java.util.List;

/** Small helpers that keep the tests short. */
final class TestTransactions {

    static final String TIMESTAMP = "2026-05-10T10:00:00Z";

    private TestTransactions() {
    }

    static Transaction credit(String accountId, String amount) {
        return new Transaction(accountId, "credit", amount(amount), TIMESTAMP);
    }

    static Transaction debit(String accountId, String amount) {
        return new Transaction(accountId, "debit", amount(amount), TIMESTAMP);
    }

    static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    static List<String> accountIds(AnalysisResult result) {
        return result.summaries().stream().map(AccountSummary::accountId).toList();
    }
}
