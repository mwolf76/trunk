package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.impl.PutSharingInformationRequestHandlerImpl;

public interface PutSharingInformationRequestHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static PutSharingInformationRequestHandler create() {
        return new PutSharingInformationRequestHandlerImpl();
    }
}
