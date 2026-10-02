package dev.moneet.contextos;

/**
 * A step from an item in one domain to a target in another, e.g. from a log
 * evidence item to the class named by its logger.
 *
 * @param fromId       id of the item the link starts from
 * @param fromTitle    title of that item
 * @param toDomain     domain of the target: {@code code} or {@code sql}
 * @param scope        repository name (code) or database service name (sql)
 * @param target       provider target: a symbol id or a table name
 * @param targetTitle  readable target, e.g. a symbol's display name
 * @param via          what connects them, e.g. "logger" or "endpoint POST /payments"
 * @param weight       score of the item the link starts from; scales the linked items
 */
public record Link(String fromId,
                   String fromTitle,
                   String toDomain,
                   String scope,
                   String target,
                   String targetTitle,
                   String via,
                   double weight) {

    /** Identifies the target; links to the same target are resolved once, at the highest weight. */
    String targetKey() {
        return toDomain + "|" + scope + "|" + target.toLowerCase();
    }
}
