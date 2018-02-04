package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.impl.GetResourceRequestHandlerImpl;

public interface GetResourceRequestHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static GetResourceRequestHandler create() {
        return new GetResourceRequestHandlerImpl();
    }
}
