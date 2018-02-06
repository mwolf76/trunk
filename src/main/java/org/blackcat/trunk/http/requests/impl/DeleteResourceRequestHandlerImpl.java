package org.blackcat.trunk.http.requests.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.PostResourceRequestHandler;
import org.blackcat.trunk.resource.impl.ErrorResource;

import java.nio.file.Path;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class DeleteResourceRequestHandlerImpl extends BaseUserRequestHandler implements PostResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(DeleteResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        Path protectedPath = protectedPath(ctx);
        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.trace("DELETE {} -> {}", protectedPath, resolvedPath);

        storage.delete(resolvedPath, resource -> {
            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;
                if (errorResource.isNotFound()) {
                    responseBuilder.notFound(ctx);
                    return;
                }

                else if (errorResource.isInvalid()) {
                    responseBuilder.conflict(ctx, errorResource.getMessage());
                    return;
                }

                else if (errorResource.isUnit()) {
                    responseBuilder.ok(ctx);
                    return;
                }
            }

            responseBuilder.internalServerError(ctx);
        });
    }
}
