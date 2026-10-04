package dev.moneet.contextos.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InputsTest {

    @Test
    void shouldIgnoreLineEndingsButNotContent(@TempDir Path dir) throws Exception {
        Path lf = Files.createDirectories(dir.resolve("lf"));
        Path crlf = Files.createDirectories(dir.resolve("crlf"));
        Files.writeString(lf.resolve("cases.json"), "{\n  \"cases\": []\n}\n");
        Files.writeString(crlf.resolve("cases.json"), "{\r\n  \"cases\": []\r\n}\r\n");

        String fingerprint = Inputs.fingerprint(lf, lf.resolve("cases.json"));
        assertEquals(fingerprint, Inputs.fingerprint(crlf, crlf.resolve("cases.json")),
                "the same checkout on Windows and Linux");

        Files.writeString(lf.resolve("cases.json"), "{\n  \"cases\": [1]\n}\n");
        assertNotEquals(fingerprint, Inputs.fingerprint(lf, lf.resolve("cases.json")));
    }

    @Test
    void shouldIncludeACasesFileOutsideTheWorkspace(@TempDir Path dir) throws Exception {
        Path workspace = Files.createDirectories(dir.resolve("workspace"));
        Files.writeString(workspace.resolve("contextos.json"), "{}");
        Path cases = Files.writeString(dir.resolve("cases.json"), "{\"cases\": []}");

        String before = Inputs.fingerprint(workspace, cases);
        Files.writeString(cases, "{\"cases\": [1]}");

        assertNotEquals(before, Inputs.fingerprint(workspace, cases));
    }
}
