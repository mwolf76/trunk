package org.blackcat.trunk.http.requests.handlers;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.middleware.UserInfoHandler;
import org.blackcat.trunk.http.requests.handlers.impl.GetSharingInformationHandlerImpl;

public interface GetSharingInformationRequestHandler extends Handler<RoutingContext> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static UserInfoHandler create() {
        return new GetSharingInformationHandlerImpl();
    }
}
