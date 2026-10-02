package dev.moneet.contextos.incident.source;

import com.fasterxml.jackson.databind.JsonNode;
import dev.moneet.contextos.incident.domain.DependencyKind;
import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.domain.ServiceKind;
import dev.moneet.contextos.incident.domain.ServiceTopology;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code services.json}:
 * <pre>
 * { "services":     [ {"name", "kind", "description"?, "repository"?} ],
 *   "dependencies": [ {"from", "to", "kind"} ] }
 * </pre>
 */
final class TopologyReader {

    ServiceTopology read(Path root, String file) {
        JsonNode document = Json.readDocument(root, file);

        List<Service> services = new ArrayList<>();
        for (JsonNode node : document.path("services")) {
            String name = Json.text(node, "name", file);
            services.add(new Service(name,
                    Json.enumValue(ServiceKind.class, Json.text(node, "kind", file + " service " + name), file),
                    Json.optionalText(node, "description"),
                    Json.optionalText(node, "repository")));
        }

        List<ServiceDependency> dependencies = new ArrayList<>();
        for (JsonNode node : document.path("dependencies")) {
            ServiceDependency dependency = new ServiceDependency(
                    Json.text(node, "from", file),
                    Json.text(node, "to", file),
                    Json.enumValue(DependencyKind.class, Json.text(node, "kind", file), file));

            for (String service : List.of(dependency.from(), dependency.to())) {
                if (services.stream().noneMatch(s -> s.name().equals(service))) {
                    throw new IllegalArgumentException(
                            "Dependency refers to unknown service '" + service + "' in " + file);
                }
            }
            dependencies.add(dependency);
        }

        return new ServiceTopology(services, dependencies);
    }
}
