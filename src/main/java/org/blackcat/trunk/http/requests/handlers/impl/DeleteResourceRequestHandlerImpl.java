package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.DeleteResourceRequestHandler;
import org.blackcat.trunk.resource.impl.ErrorResource;

import java.nio.file.Path;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class DeleteResourceRequestHandlerImpl extends BaseUserRequestHandler implements DeleteResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(DeleteResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkJsonRequest(ctx, ok -> {
            Path protectedPath = protectedPath(ctx);
            Path resolvedPath = storage.getRoot().resolve(protectedPath);
            logger.trace("DELETE {} -> {}", protectedPath, resolvedPath);

            storage.delete(resolvedPath, resource -> {
                if (resource instanceof ErrorResource) {
                    ErrorResource errorResource = (ErrorResource) resource;
                    if (errorResource.isNotFound()) {
                        jsonResponseBuilder.notFound(ctx);
                    } else if (errorResource.isInvalid()) {
                        jsonResponseBuilder.conflict(ctx);
                    } else if (errorResource.isUnit()) {
                        logger.debug("Successfully deleted {}", ctx.request().uri());
                        jsonResponseBuilder.success(ctx, new JsonObject());
                    }
                } else {
                    logger.error("Unexpected error resource type");
                    ctx.fail(new BaseUserRequestException("Unexpected error resource type"));
                }
            });
        });
    }
}
