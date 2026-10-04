# Schema Context Engine – Architecture

## Overview

The system is structured as a layered schema reasoning engine:

JDBC → Domain → Graph → Context

Each layer has a strict responsibility boundary.

---

## Layer 1: JDBC (Extraction Layer)

Responsible for extracting database metadata via `DatabaseMetaData`.

Produces:
- DatabaseSchema
- Tables
- Columns
- Primary Keys
- Foreign Keys

This layer does not perform traversal or reasoning.

---

## Layer 2: Domain (Structural Model)

Represents the immutable schema model.

Core entities:
- DatabaseSchema
- Table
- Column
- PrimaryKey
- ForeignKey

This layer is purely structural and contains no graph logic.

---

## Layer 3: Graph (Relationship & Reasoning Layer)

### v0.1
Initial adjacency-based graph:
- Table → Set<Table>
- DFS traversal
- Subgraph extraction

### v0.2
Introduced:
- TraversalStrategy abstraction (DFS/BFS)
- Level grouping
- Distance mapping
- Directional traversal (dependencies / dependents / bidirectional)

### v0.3 (Edge-Based Refactor)

The graph was refactored from table-only adjacency to an edge-aware model.

Internal representation upgraded to:

Map<String, Set<GraphEdge>> outgoing  
Map<String, Set<GraphEdge>> incoming

### GraphEdge

Represents a foreign key relationship:

fromTable.fromColumn → toTable.toColumn

This enables:

- Shortest path reconstruction
- Explicit join hint generation
- Direction-aware traversal
- Edge-based reasoning

### Directional Traversal

TraversalDirection:
- DEPENDENCIES_ONLY (upstream)
- DEPENDENTS_ONLY (downstream)
- BIDIRECTIONAL (structural neighborhood)

This allows semantic control over graph exploration.

### Shortest Path

Added:

getShortestPathEdges(from, to, direction)

This returns an ordered list of GraphEdge objects forming the minimal join chain.

This enables:

- Automatic join path explanation
- Automatic depth calculation
- Deterministic SQL guidance
- Reduced hallucination in LLM pipelines

--- 
## Layer 4: Context (Formatting & Selection Layer)

Responsible for:

- Selecting relevant tables
- Applying traversal strategy
- Formatting structured schema context

ContextStrategy implementations can now:

- Choose traversal strategy (DFS/BFS)
- Choose traversal direction
- Limit by depth
- Use shortest path for join hints

The context layer does not implement traversal logic.

---

## Architectural Principles

1. Separation of concerns
2. Internal refactoring without breaking public APIs
3. Strategy-based traversal
4. Directional semantics for explicit reasoning
5. Deterministic behavior (LinkedHashMap / LinkedHashSet)

---

## Evolution Summary

v0.1 → Structural adjacency graph  
v0.2 → Strategy-based traversal + direction control  
v0.3 → Edge-aware graph + shortest path + join hint foundation

The graph layer is now a semantic schema reasoning engine rather than a simple adjacency map.


# Traversal Evolution (v0.2)

## Motivation

DFS provided reachability but lacked distance awareness.
To enable relevance-based context prioritization,
a traversal abstraction was introduced.

## Design Decision

Instead of modifying existing APIs:
- TraversalStrategy interface was added.
- Existing DFS logic was extracted into DfsTraversalStrategy.
- BFS was introduced as an additional strategy.

This preserves backward compatibility and keeps the graph layer extensible.