package dev.moneet.contextos.eval;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * A fingerprint of everything a run's results depend on: the rubric and the
 * workspace (fixtures, code, configuration). Recorded with the results, so a
 * resumed run can't mix trials graded against different inputs. Line endings
 * are normalised, so the fingerprint is the same on every checkout.
 */
final class Inputs {

    private Inputs() {
    }

    static String fingerprint(Path workspace, Path cases) {
        MessageDigest sha = sha256();
        List<Path> files = new ArrayList<>(files(workspace));
        Path casesFile = cases.toAbsolutePath().normalize();
        if (!casesFile.startsWith(workspace.toAbsolutePath().normalize())) {
            files.add(casesFile);
        }
        for (Path file : files) {
            String name = workspace.toAbsolutePath().normalize().relativize(file).toString().replace('\\', '/');
            sha.update(name.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            sha.update(normalised(file));
            sha.update((byte) 0);
        }
        return HexFormat.of().formatHex(sha.digest()).substring(0, 16);
    }

    private static List<Path> files(Path root) {
        try (Stream<Path> paths = Files.walk(root.toAbsolutePath().normalize())) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + root, e);
        }
    }

    private static byte[] normalised(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                boolean crBeforeLf = bytes[i] == '\r' && i + 1 < bytes.length && bytes[i + 1] == '\n';
                if (!crBeforeLf) {
                    out.write(bytes[i]);
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
