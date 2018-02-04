package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.impl.PostResourceRequestHandlerImpl;

public interface PostResourceRequestHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static PostResourceRequestHandler create() {
        return new PostResourceRequestHandlerImpl();
    }
}
