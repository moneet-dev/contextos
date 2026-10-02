package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;
import dev.moneet.contextos.core.graph.Reached;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Renders items grouped by file. Files appear in order of their best-ranked
 * item; within a file, items appear in source order. Each item is preceded by
 * a provenance comment: why it was selected, its score, and (when more than
 * one hop away) the path from the target.
 */
public final class CodeRenderer {

    public String render(List<CodeContextItem> items, RenderMode mode) {

        Map<String, List<CodeContextItem>> byFile = new LinkedHashMap<>();
        for (CodeContextItem item : items) {
            byFile.computeIfAbsent(item.symbol().location().file(), k -> new ArrayList<>()).add(item);
        }

        Map<String, String> displayNames = items.stream()
                .collect(Collectors.toMap(i -> i.symbol().id(), i -> i.symbol().displayName(), (a, b) -> a));

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<CodeContextItem>> file : byFile.entrySet()) {

            sb.append("File ").append(file.getKey()).append(":\n\n");

            List<CodeContextItem> inSourceOrder = file.getValue().stream()
                    .sorted(Comparator.comparingInt(i -> i.symbol().location().startLine()))
                    .toList();

            for (CodeContextItem item : inSourceOrder) {
                Symbol symbol = item.symbol();

                sb.append("// ").append(symbol.displayName())
                        .append(" (line ").append(symbol.location().startLine()).append(")")
                        .append(" - ").append(item.reason())
                        .append(String.format(" [score %.2f]", item.score()))
                        .append("\n");

                if (item.distance() > 1) {
                    sb.append("//   path: ").append(path(item, id -> displayNames.getOrDefault(id, id)))
                            .append("\n");
                }

                if (!item.dataAccess().isEmpty()) {
                    sb.append("//   data access: ")
                            .append(item.dataAccess().stream()
                                    .map(Object::toString)
                                    .distinct()
                                    .collect(Collectors.joining(", ")))
                            .append("\n");
                }

                sb.append(text(item, mode)).append("\n\n");
            }
        }

        return sb.toString();
    }

    /** The item's code: member source in FULL mode, otherwise the signature (types are always headers). */
    public String text(CodeContextItem item, RenderMode mode) {
        Symbol symbol = item.symbol();
        return mode == RenderMode.FULL && !symbol.kind().isType()
                ? dedent(symbol.source())
                : symbol.signature();
    }

    /** E.g. {@code A -CALLS-> B <-IMPLEMENTS- C}. */
    static String path(CodeContextItem item, Function<String, String> name) {
        List<String> nodes = new Reached<>(item.symbol().id(), item.distance(), item.path()).nodes();
        StringBuilder sb = new StringBuilder(name.apply(nodes.get(0)));

        for (int i = 0; i < item.path().size(); i++) {
            SymbolReference edge = item.path().get(i);
            String from = nodes.get(i);
            sb.append(edge.sourceId().equals(from)
                    ? " -" + edge.kind() + "-> "
                    : " <-" + edge.kind() + "- ");
            sb.append(name.apply(nodes.get(i + 1)));
        }
        return sb.toString();
    }

    private static String dedent(String text) {
        List<String> lines = text.lines().toList();
        int indent = lines.stream()
                .filter(line -> !line.isBlank())
                .mapToInt(line -> line.length() - line.stripLeading().length())
                .min()
                .orElse(0);
        return lines.stream()
                .map(line -> line.isBlank() ? "" : line.substring(indent))
                .collect(Collectors.joining("\n"));
    }
}
