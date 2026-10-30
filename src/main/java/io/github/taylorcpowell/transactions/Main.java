package io.github.taylorcpowell.transactions;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point. Reads a JSON array of transactions from the argument file,
 * or from standard input when there is no argument, and prints the analysis as JSON.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err));
    }

    /**
     * Runs the program and returns exit code 0 on success, or 1 after printing what went
     * wrong with the arguments, the input, or the output. Other exceptions indicate a bug, so they
     * aren't caught here.
     */
    static int run(String[] args, InputStream stdin, PrintStream out, PrintStream err) {
        if (args.length > 1) {
            err.println("Usage: java -jar transaction-analyzer.jar [FILE]");
            return 1;
        }
        try (InputStream input = args.length == 0 ? stdin : open(args[0])) {
            List<Transaction> transactions = TransactionJson.readTransactions(input);
            out.println(TransactionJson.writeResult(new TransactionAnalyzer().analyze(transactions)));
            // PrintStream doesn't throw when a write fails. It only sets this flag.
            if (out.checkError()) {
                err.println("Error: could not write the output");
                return 1;
            }
            return 0;
        } catch (NoSuchFileException e) {
            err.println("Error: file not found: " + args[0]);
        } catch (IOException | InvalidPathException e) {
            err.println("Error: " + e.getMessage());
        }
        return 1;
    }

    /**
     * Opens the named file. A directory is rejected first, because Windows reports one with only
     * its name and Linux with only "Is a directory".
     */
    private static InputStream open(String fileName) throws IOException {
        Path path = Path.of(fileName);
        if (Files.isDirectory(path)) {
            throw new IOException(fileName + " is a directory, not a file");
        }
        return Files.newInputStream(path);
    }
}
