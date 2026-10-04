package dev.moneet.contextos.code.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;

/**
 * Finds the handler method for an HTTP route, from Spring MVC mapping annotations
 * ({@code @GetMapping}, {@code @PostMapping}, ..., {@code @RequestMapping}) on the
 * method and its class. Path variables match regardless of name, so
 * {@code POST /payments/{id}/refund} finds {@code @PostMapping("/{paymentId}/refund")}
 * under {@code @RequestMapping("/payments")}.
 */

// SamBuilds
// Added Regex to handle jax-rs requests

/* path - "@Path\\(\"([^\]*)\"\\)"
 * verb - "@(GET|POST|PUT|DELETE|PATCH)\\b" 
*/
public final class EndpointLookup {

    private static final Pattern MAPPING =
            Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping\\b(\\(([^)]*)\\))?");
    private static final Pattern FIRST_STRING = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]*}");
    private static final Pattern HTTP_METHOD = Pattern.compile("RequestMethod\\.(\\w+)");

    private static final Pattern JAX_RS_PATH = Pattern.compile("@Path\\(\"([^\"]*)\"\\)");
    private static final Pattern JAX_RS_VERB = Pattern.compile("@(GET|PUT|POST|DELETE|PATCH)\\b");

    private record Endpoint(String method, String path, Symbol handler) {
    }

    private final List<Endpoint> endpoints = new ArrayList<>();

    public EndpointLookup(CodeRepository repository) {
        for (Symbol symbol : repository.getSymbols()) {
            if (symbol.kind() != SymbolKind.METHOD || symbol.parentId() == null) {
                continue;
            }
            Optional<Mapping> method = mapping(symbol.signature());
            if (method.isEmpty()) {
                continue;
            }
            String prefix = mapping(repository.getSymbol(symbol.parentId()).signature())
                    .map(Mapping::path)
                    .orElse("");
            endpoints.add(new Endpoint(method.get().method(), normalize(prefix + "/" + method.get().path()), symbol));
        }
    }

    /** Handler for e.g. {@code "POST /payments/{id}/refund"}; empty if the text is not a route or nothing matches. */
    public Optional<Symbol> find(String operation) {
        String[] parts = operation.trim().split("\\s+", 2);
        if (parts.length != 2 || !parts[1].startsWith("/")) {
            return Optional.empty();
        }
        return find(parts[0], parts[1]);
    }

    public Optional<Symbol> find(String httpMethod, String path) {
        String method = httpMethod.toUpperCase(Locale.ROOT);
        String normalized = normalize(path);
        return endpoints.stream()
                .filter(e -> e.path().equals(normalized))
                .filter(e -> e.method() == null || e.method().equals(method))
                .map(Endpoint::handler)
                .findFirst();
    }

    private record Mapping(String method, String path) {
    }

    /** First mapping annotation in a signature; method is null for @RequestMapping without a method. */
    private static Optional<Mapping> mapping(String signature) {
        Matcher matcher = MAPPING.matcher(signature);
        if (!matcher.find()) {
            Matcher pathMatcher = JAX_RS_PATH.matcher(signature);
            Matcher verbMatcher = JAX_RS_VERB.matcher(signature);
            boolean haspath = pathMatcher.find();
            boolean hasverb = verbMatcher.find();
            if (!haspath && !hasverb) return Optional.empty();
            String path = haspath ? pathMatcher.group(1) : "";
            String verb = hasverb ? verbMatcher.group(1) : "";
            return Optional.of(new Mapping(verb,path));
        }
        String arguments = matcher.group(3) == null ? "" : matcher.group(3);

        Matcher string = FIRST_STRING.matcher(arguments);
        String path = string.find() ? string.group(1) : "";

        String method;
        if (matcher.group(1).equals("Request")) {
            Matcher explicit = HTTP_METHOD.matcher(arguments);
            method = explicit.find() ? explicit.group(1) : null;
        } else {
            method = matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return Optional.of(new Mapping(method, path));
    }

    /** Leading slash, no trailing or doubled slashes, path variables as {}. */
    private static String normalize(String path) {
        String result = PATH_VARIABLE.matcher(path).replaceAll("{}").replaceAll("/+", "/");
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        return result.length() > 1 && result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }
}
