package dev.moneet.schema.graph;

public final class JoinAnalysisResult {

    private final GraphEdge edge;
    private final JoinQuality quality;

    public JoinAnalysisResult(GraphEdge edge,
                              JoinQuality quality) {
        this.edge = edge;
        this.quality = quality;
    }

    public GraphEdge getEdge() {
        return edge;
    }

    public JoinQuality getQuality() {
        return quality;
    }
}