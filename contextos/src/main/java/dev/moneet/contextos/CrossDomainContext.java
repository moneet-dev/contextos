package dev.moneet.contextos;

import dev.moneet.contextos.core.context.ContextPackage;

import java.util.List;

/** The packed context for an investigation and the links that assembled it. */
public record CrossDomainContext(ContextPackage contextPackage, List<Link> links) {

    public CrossDomainContext {
        links = List.copyOf(links);
    }

    public String rendered() {
        return contextPackage.rendered();
    }

    @Override
    public String toString() {
        return rendered();
    }
}
