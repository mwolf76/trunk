package org.blackcat.trunk.http;

import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.resource.impl.ErrorResource;

public class ErrorResourceHandler {
    public static void invoke(ErrorResource errorResource, RoutingContext ctx, ResponseBuilder responseBuilder) {
        if (errorResource.isNotFound())
            responseBuilder.notFound(ctx);
        else
            responseBuilder.internalServerError(ctx);
    }
}
