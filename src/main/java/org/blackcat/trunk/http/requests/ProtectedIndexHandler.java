package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.impl.ProtectedIndexHandlerImpl;

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