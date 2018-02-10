package org.blackcat.trunk.http.requests.response;

import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.ResponseStatus;

final public class ResponseUtils {

    static public void found(RoutingContext ctx, String redirectURI) {
        ctx.response()
            .setStatusCode(ResponseStatus.FOUND.getStatusCode())
            .setStatusMessage(ResponseStatus.FOUND.getStatusMessage())
            .putHeader(Headers.LOCATION_HEADER, redirectURI)
            .end();
    }

    static public void complete(RoutingContext ctx) {
        ctx.response().end();
    }

}
