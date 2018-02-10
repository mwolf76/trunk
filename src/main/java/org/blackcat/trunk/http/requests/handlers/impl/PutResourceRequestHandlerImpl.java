package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.handlers.PutResourceRequestHandler;
import org.blackcat.trunk.resource.impl.ErrorResource;

import java.nio.file.Path;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class PutResourceRequestHandlerImpl extends BaseUserRequestHandler implements PutResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(PutResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        if (ctx.get("requestType").equals(RequestType.HTML))
            jsonResponseBuilder.badRequest(ctx);

        else {
            Path protectedPath = protectedPath(ctx);

            MultiMap headers = ctx.request().headers();
            String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

            Path resolvedPath = storage.getRoot().resolve(protectedPath);
            logger.trace("PUT {} -> {} [etag = {}]", protectedPath, resolvedPath, etag);

            storage.putCollectionResource(resolvedPath, resource -> {
                if (resource instanceof ErrorResource) {
                    ErrorResource errorResource = (ErrorResource) resource;
                    if (errorResource.isUnit()) {
                        logger.debug("Ok: {}", ctx.request().uri());
                        jsonResponseBuilder.success(ctx, new JsonObject());
                    } else if (errorResource.isInvalid()) {
                        logger.debug("INVALID: {}", ctx.request().uri());
                        jsonResponseBuilder.conflict(ctx);
                    } else {
                        logger.debug("UNEXPECTED: {}", ctx.request().uri());
                        ctx.fail(new BaseUserRequestException("Unexpected error"));
                    }
                    return;
                }
            });
        }
    }
}
