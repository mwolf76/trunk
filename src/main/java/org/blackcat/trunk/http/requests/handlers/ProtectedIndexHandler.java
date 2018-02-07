package org.blackcat.trunk.http.requests.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.impl.ProtectedIndexHandlerImpl;

public interface ProtectedIndexHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static ProtectedIndexHandler create() {
        return new ProtectedIndexHandlerImpl();
    }
}