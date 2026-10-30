# Transaction Analyzer

Reads a list of financial transactions and returns, for each account, the total credits, total debits, ending balance, largest transaction, number of valid transactions, and whether the account ends overdrawn. It also names `topSpender`, the account with the highest total debits. Invalid transactions are ignored.

## How to run

Requires JDK 17 or newer. The Maven wrapper downloads everything else on first run.

```bash
git clone https://github.com/Taylor-C-Powell/transaction-analyzer-final.git
cd transaction-analyzer-final
./mvnw test        # run the tests
./mvnw package     # run the tests, then build target/transaction-analyzer.jar
java -jar target/transaction-analyzer.jar examples/example-input.json
```

> [!NOTE]
> On Windows, type `.\mvnw.cmd` instead of `./mvnw`.

When run with no file argument, the program reads standard input. It either prints the result as JSON and exits with 0, or prints an error and exits with 1.

With `examples/example-input.json` as input, the program's output matches the exercise's expected output, saved as `examples/example-output.json`. To see most of the assumptions below in action, run the program on `examples/edge-cases-input.json` and compare the output with `examples/edge-cases-output.json`. JUnit tests check both examples automatically.

## Assumptions

The program follows all provided rules. When the rules don't address a case, the program rejects anything it would have to guess at and ignores problems that can't affect results.

| Case | Decision |
| --- | --- |
| Representing money | `BigDecimal`, with values read straight from the input JSON, so 0.1 + 0.2 is exactly 0.3. Results print in shortest form so that equal amounts always look the same. |
| Currency | Every amount is assumed to be in the same currency since the input has no currency field. |
| "Credit" or " debit " as the transaction type | Invalid. Only the exact values `credit` and `debit` count. |
| Blank or differently cased IDs | An ID of only whitespace is uninformative, so invalid. Otherwise, IDs are used exactly as given, spaces and case included, so "A100", "a100", and " A100" are different accounts. |
| Sort order of IDs | Lexicographic, so "A10" sorts before "A2", and uppercase comes before lowercase. The same sorting scheme resolves `topSpender` ties. |
| Missing, null, or non-numeric amount | Invalid, like an amount of 0 or less. A string such as "10.00" is invalid. |
| Amounts with too many digits | Invalid if either side of the decimal point has more than 30 digits, so input like `1e999999999` can't stall or crash the run. Zeros at the end of the decimal part don't count. A number Jackson can't read at all, such as one over 1,000 characters long, stops the program with an error. |
| Missing or malformed timestamp | The timestamp is ignored, and the transaction still counts. The rules mention timestamps only to say input may be unsorted, and no output depends on them. |
| A JSON field with the wrong value type | Counts as missing, so a number as the `accountId` makes the transaction invalid. |
| Unknown fields | Fields other than `accountId`, `type`, `amount`, and `timestamp` are ignored. |
| An array entry that isn't an object, such as `null` | Invalid. |
| Input that isn't a valid JSON array | Stops the program with an error. |
| A field given twice in one transaction | Stops the program with an error, even for a field the program doesn't use. A sender that repeats fields is broken, and there's no telling which value was meant. |
| Unsorted input | Doesn't matter. Sums, counts, the largest amount, and the ending balance are the same in any order, so transactions are never sorted by time. Order would matter only for flagging an account the moment it goes overdrawn. |
| Identical transactions | Both count. Without a transaction ID, a duplicate looks the same as two real transactions, such as two equal fees posted together, and dropping a real one is as wrong as counting a copy. |
| An account with only invalid transactions | Never created, so it doesn't appear. An account is added only when its first valid transaction arrives. |
| `topSpender` with no debits | When there are no accounts, `topSpender` is `null`. Otherwise, every account ties at 0 and the smallest `accountId` wins. Ties compare by value, so 50 equals 50.00. |
| Ending balance of exactly 0 | Not overdrawn. |

## Design

The program runs in four steps:

1. `Main` opens the file, or uses standard input when no file is given.
2. `TransactionJson` turns the JSON into `Transaction` records using Jackson, since Java SE has no JSON parser.
3. `TransactionAnalyzer` makes one pass over all the transactions. `TransactionValidator` checks each one against the rules and returns a `ValidTransaction` only when it passes. Each valid transaction updates its account's totals in a `HashMap`. The accounts are then sorted by ID once, and a comparator picks the top spender: highest debits first, then the smaller ID. That's O(n + m log m) time for n transactions across m accounts.
4. `TransactionJson` writes the result, and `Main` prints it and sets the exit code.

Only `Main` and `TransactionJson` deal with input, output, and JSON. `TransactionAnalyzer` works on plain Java objects.

Trade-offs:

- The whole input is held in memory, both as Jackson's tree and as a list of records. Reading it with Jackson's streaming parser, one transaction at a time, would keep memory proportional to the number of accounts.
- `TransactionJson` reads a JSON tree and maps the four fields itself. With Jackson's default settings, binding the JSON straight to the records would accept `100` as an ID and `"10.00"` as an amount, going against the assumptions above.
- Invalid transactions are ignored. A production version would report each one and the rule it broke.

## Tests

`./mvnw test` runs 88 JUnit 5 tests, each named for the behavior it checks. They cover every validity rule, the exercise's example, exact decimals, shuffled input, overdrawn accounts, the top spender and its ties, JSON reading and writing, and the command line end to end on both example files. GitHub Actions runs them on Java 17, 21, and 25.
