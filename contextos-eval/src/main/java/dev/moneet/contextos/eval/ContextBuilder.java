package dev.moneet.contextos.eval;

import dev.moneet.contextos.ContextOS;
import dev.moneet.contextos.Workspace;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.core.context.TokenEstimator;
import dev.moneet.contextos.incident.context.IncidentContextProvider;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.graph.ServiceGraphBuilder;

/** Builds the context each condition gives the model, under the same budget. */
public final class ContextBuilder {

    private final Workspace workspace;
    private final ContextOS contextOS;
    private final IncidentContextProvider incidents;
    private final RawTelemetryContext raw;
    private final TokenEstimator estimator = TokenEstimator.defaultEstimator();

    public ContextBuilder(Workspace workspace) {
        this.workspace = workspace;
        this.contextOS = workspace.contextOS();
        this.incidents = new IncidentContextProvider(workspace.runtime(),
                new ServiceGraphBuilder().build(workspace.runtime().getTopology()));
        this.raw = new RawTelemetryContext(workspace.root().resolve("runtime"), estimator);
    }

    public Incident incident(String id) {
        return workspace.runtime().getIncident(id);
    }

    public String build(Condition condition, String incidentId, int budgetTokens) {
        ContextBudget budget = ContextBudget.tokens(budgetTokens);
        return switch (condition) {
            case RAW_TELEMETRY -> raw.build(incident(incidentId), budgetTokens);
            case INCIDENT_CONTEXT -> incidents.provide(new ContextRequest(incidentId, 2, budget)).rendered();
            case CROSS_DOMAIN -> contextOS.investigate(incidentId, budget).rendered();
        };
    }

    public int tokens(String text) {
        return estimator.estimate(text);
    }
}
