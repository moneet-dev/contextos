package dev.moneet.contextos.code.source;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.moneet.contextos.code.domain.CodeFile;
import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.DataAccess;
import dev.moneet.contextos.code.domain.SymbolReference;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads a Java source tree with JavaParser. Only the repository's own sources and
 * the JDK are used for symbol resolution; third-party dependencies are not
 * required, and unresolvable references fall back to name-based matching.
 */
public class JavaRepositorySource implements RepositorySource {

    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of(".git", ".gradle", ".idea", "build", "target", "out", "node_modules");

    private final Path root;
    private final ParserConfiguration configuration;

    public JavaRepositorySource(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Override
    public CodeRepository load() {
        Map<Path, CompilationUnit> units = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        JavaParser parser = new JavaParser(configuration);

        for (Path file : javaFiles()) {
            ParseResult<CompilationUnit> result = parse(parser, file);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                units.put(file, result.getResult().get());
            } else {
                skipped.add(relative(file));
            }
        }

        JavaSymbolSolver solver = new JavaSymbolSolver(typeSolver(units));
        units.values().forEach(solver::inject);

        SymbolIndex index = new SymbolIndex();
        SymbolExtractor symbolExtractor = new SymbolExtractor();
        List<CodeFile> files = new ArrayList<>();

        for (Map.Entry<Path, CompilationUnit> unit : units.entrySet()) {
            CompilationUnit cu = unit.getValue();
            String file = relative(unit.getKey());

            files.add(new CodeFile(file,
                    cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse(""),
                    cu.getImports().stream().map(i -> i.getNameAsString()).toList()));

            symbolExtractor.extract(cu, file, readLines(unit.getKey()), index);
        }

        ReferenceExtractor referenceExtractor = new ReferenceExtractor(index);
        DataAccessExtractor dataAccessExtractor = new DataAccessExtractor(index);
        List<SymbolReference> references = new ArrayList<>();
        List<DataAccess> dataAccess = new ArrayList<>();

        for (CompilationUnit cu : units.values()) {
            references.addAll(referenceExtractor.extract(cu));
            dataAccess.addAll(dataAccessExtractor.extract(cu));
        }
        references.addAll(referenceExtractor.overrides(references));

        return new CodeRepository(root.toString(), files, index.symbols(),
                distinct(references), dataAccess, skipped);
    }

    private List<Path> javaFiles() {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(p))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list sources under: " + root, e);
        }
    }

    private boolean isExcluded(Path file) {
        for (Path segment : root.relativize(file)) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Source roots derived from package declarations, e.g. .../src/main/java. */
    private CombinedTypeSolver typeSolver(Map<Path, CompilationUnit> units) {
        Set<Path> sourceRoots = new LinkedHashSet<>();

        for (Map.Entry<Path, CompilationUnit> unit : units.entrySet()) {
            Path directory = unit.getKey().getParent();
            String pkg = unit.getValue().getPackageDeclaration()
                    .map(p -> p.getNameAsString())
                    .orElse("");

            Path sourceRoot = directory;
            if (!pkg.isEmpty()) {
                Path packagePath = Path.of("", pkg.split("\\."));
                if (!directory.endsWith(packagePath)) {
                    continue;
                }
                for (int i = 0; i < packagePath.getNameCount(); i++) {
                    sourceRoot = sourceRoot.getParent();
                }
            }
            sourceRoots.add(sourceRoot);
        }

        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(true));
        for (Path sourceRoot : sourceRoots) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot, configuration));
        }
        return typeSolver;
    }

    private static ParseResult<CompilationUnit> parse(JavaParser parser, Path file) {
        try {
            return parser.parse(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read: " + file, e);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read: " + file, e);
        }
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** Drops self-references and repeats of the same (source, target, kind), keeping the first. */
    private static List<SymbolReference> distinct(List<SymbolReference> references) {
        Map<String, SymbolReference> unique = new LinkedHashMap<>();
        for (SymbolReference reference : references) {
            if (!reference.sourceId().equals(reference.targetId())) {
                unique.putIfAbsent(reference.sourceId() + "|" + reference.targetId() + "|" + reference.kind(),
                        reference);
            }
        }
        return List.copyOf(unique.values());
    }
}
