package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.DeleteResourceRequestHandler;
import org.blackcat.trunk.resource.exceptions.NotFoundException;

import java.nio.file.Path;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class DeleteResourceRequestHandlerImpl extends BaseUserRequestHandler implements DeleteResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(DeleteResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkJsonRequest(ctx, ok -> {
            Path protectedPath = protectedPath(ctx);
            logger.info("Deleting resource {}", protectedPath);

            Path resolvedPath = storage.getRoot().resolve(protectedPath);
            storage.delete(resolvedPath, asyncResult -> {
                if (asyncResult.failed()) {
                    Throwable cause = asyncResult.cause();
                    if (cause instanceof NotFoundException) {
                        logger.warn("Resource not found.");
                        jsonResponseBuilder.notFound(ctx);
                    } else {
                        logger.warn("Could not delete resource {}.", asyncResult.cause());
                        jsonResponseBuilder.conflict(ctx);
                    }
                } else {
                    logger.info("Successfully deleted {}", ctx.request().uri());
                    jsonResponseBuilder.success(ctx, new JsonObject());
                }
            });
        });
    }
}
