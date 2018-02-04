package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.impl.PublicIndexHandlerImpl;

public interface PublicIndexHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static PublicIndexHandler create() {
        return new PublicIndexHandlerImpl();
    }
}