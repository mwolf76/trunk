package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.PutResourceRequestHandler;

import java.nio.file.Path;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class PutResourceRequestHandlerImpl extends BaseUserRequestHandler implements PutResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(PutResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        checkJsonRequest(ctx, ok -> {
            Path protectedPath = protectedPath(ctx);
            Path resolvedPath = storage.getRoot().resolve(protectedPath);
            logger.trace("PUT {} -> {}", protectedPath, resolvedPath);

            storage.putCollectionResource(resolvedPath, resourceAsyncResult -> {
                String requestURI = ctx.request().uri();
                if (resourceAsyncResult.failed()) {
                    logger.debug("INVALID: {}", requestURI);
                    jsonResponseBuilder.conflict(ctx);
                } else {
                    logger.debug("Ok: {}", requestURI);
                    jsonResponseBuilder.success(ctx, new JsonObject());
                }
            });
        });
    }
}
