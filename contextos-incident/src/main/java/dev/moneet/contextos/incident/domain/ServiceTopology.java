package dev.moneet.contextos.incident.domain;

import java.util.List;
import java.util.Objects;

public final class ServiceTopology {

    private final List<Service> services;
    private final List<ServiceDependency> dependencies;

    public ServiceTopology(List<Service> services, List<ServiceDependency> dependencies) {
        Objects.requireNonNull(services, "services must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        this.services = List.copyOf(services);
        this.dependencies = List.copyOf(dependencies);
    }

    public List<Service> getServices() {
        return services;
    }

    public List<ServiceDependency> getDependencies() {
        return dependencies;
    }

    public Service getService(String name) {
        return services.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + name));
    }

    public boolean containsService(String name) {
        return services.stream().anyMatch(s -> s.name().equals(name));
    }

    @Override
    public String toString() {
        return "ServiceTopology{" +
                "services=" + services.size() +
                ", dependencies=" + dependencies.size() +
                '}';
    }
}
