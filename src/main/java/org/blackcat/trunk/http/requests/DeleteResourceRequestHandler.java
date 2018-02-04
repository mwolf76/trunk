package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.impl.DeleteResourceRequestHandlerImpl;

public interface DeleteResourceRequestHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static PostResourceRequestHandler create() {
        return new DeleteResourceRequestHandlerImpl();
    }
}
