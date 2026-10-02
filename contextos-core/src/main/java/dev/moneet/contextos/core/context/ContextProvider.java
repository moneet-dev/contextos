package dev.moneet.contextos.core.context;

import java.util.List;

/**
 * Produces context items for one domain. Providers rank items; packing them
 * into a budget is left to {@link ContextPackager}, so items from several
 * providers can share one budget.
 */
public interface ContextProvider {

    /** Domain name used on the items, e.g. {@code sql}, {@code code}, {@code incident}. */
    String domain();

    /** Items relevant to {@code request}, highest score first. */
    List<ContextItem> collect(ContextRequest request);

    /** Collects and packs this provider's items under the request budget. */
    default ContextPackage provide(ContextRequest request) {
        return new ContextPackager().pack(request, collect(request));
    }
}
