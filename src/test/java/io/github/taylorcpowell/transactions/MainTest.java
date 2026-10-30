package io.github.taylorcpowell.transactions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** End-to-end tests: real files in, printed JSON and exit codes out. */
class MainTest {

    private static final Path EXAMPLES = Path.of("examples");

    @ParameterizedTest(name = "{0} prints {1}")
    @CsvSource({
        "example-input.json,    example-output.json",
        "edge-cases-input.json, edge-cases-output.json"
    })
    void printsTheExpectedOutputForEachExampleFile(String input, String expectedOutput) throws IOException {
        Run run = run("", EXAMPLES.resolve(input).toString());

        assertEquals(0, run.exitCode(), run.err());
        assertEquals(contentsOf(expectedOutput), normalize(run.out()));
        assertEquals("", run.err());
    }

    @Test
    void readsStandardInputWhenNoFileIsGiven() throws IOException {
        Run run = run(Files.readString(EXAMPLES.resolve("example-input.json")));

        assertEquals(0, run.exitCode(), run.err());
        assertEquals(contentsOf("example-output.json"), normalize(run.out()));
    }

    @Test
    void reportsAFileThatDoesNotExist() {
        Run run = run("", "no-such-file.json");

        assertEquals(1, run.exitCode());
        assertEquals("Error: file not found: no-such-file.json", normalize(run.err()));
        assertEquals("", run.out());
    }

    @Test
    void reportsADirectoryInsteadOfAFile() {
        Run run = run("", EXAMPLES.toString());

        assertEquals(1, run.exitCode());
        assertEquals("Error: examples is a directory, not a file", normalize(run.err()));
        assertEquals("", run.out());
    }

    @Test
    void reportsAFileNameThatCannotBeAPath() {
        // No operating system allows a null character in a file name, so Path.of rejects it everywhere.
        Run run = run("", "bad" + (char) 0 + "name.json");

        assertEquals(1, run.exitCode());
        assertTrue(run.err().startsWith("Error: "), run.err());
        assertEquals("", run.out());
    }

    @Test
    void reportsInputThatIsNotValidJson() {
        Run run = run("[{ \"accountId\": ");

        assertEquals(1, run.exitCode());
        assertTrue(run.err().startsWith("Error: the input is not valid JSON"), run.err());
        assertEquals("", run.out());
    }

    @Test
    void printsUsageWhenGivenMoreThanOneFile() {
        Run run = run("", "a.json", "b.json");

        assertEquals(1, run.exitCode());
        assertEquals("Usage: java -jar transaction-analyzer.jar [FILE]", normalize(run.err()));
    }

    @Test
    void reportsOutputThatCannotBeWritten() {
        OutputStream fullDisk = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("No space left on device");
            }
        };
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(new String[] {EXAMPLES.resolve("example-input.json").toString()},
                InputStream.nullInputStream(), new PrintStream(fullDisk, true, UTF_8),
                new PrintStream(err, true, UTF_8));

        assertEquals(1, exitCode);
        assertEquals("Error: could not write the output", normalize(err.toString(UTF_8)));
    }

    private record Run(int exitCode, String out, String err) {
    }

    private static Run run(String stdin, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new ByteArrayInputStream(stdin.getBytes(UTF_8)),
                new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8));
        return new Run(exitCode, out.toString(UTF_8), err.toString(UTF_8));
    }

    private static String contentsOf(String exampleFile) throws IOException {
        return normalize(Files.readString(EXAMPLES.resolve(exampleFile)));
    }

    /** Ignores line-ending differences, so the tests pass on Windows too. */
    private static String normalize(String text) {
        return text.replace("\r\n", "\n").strip();
    }
}
