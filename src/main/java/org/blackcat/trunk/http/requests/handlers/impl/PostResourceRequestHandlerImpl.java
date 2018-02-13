package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.PostResourceRequestHandler;
import org.blackcat.trunk.resource.Resource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.streams.impl.PumpImpl;

import java.nio.file.Path;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class PostResourceRequestHandlerImpl extends BaseUserRequestHandler implements PostResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(PostResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        if (ctx.get("requestType").equals(RequestType.HTML))
            jsonResponseBuilder.badRequest(ctx);

        else {
            HttpServerRequest request = ctx.request();
            Path protectedPath = protectedPath(ctx);

            Path resolvedPath = storage.getRoot().resolve(protectedPath);
            logger.debug("POST {} -> {}", protectedPath, resolvedPath);

            storage.putDocumentResource(resolvedPath, resourceAsyncResult -> {
                if (resourceAsyncResult.failed()) {
                    Throwable cause = resourceAsyncResult.cause();
                    logger.warn("Conflict: {}", cause.toString());
                    jsonResponseBuilder.conflict(ctx);
                } else {
                    Resource resource = resourceAsyncResult.result();
                    if (resource instanceof DocumentContentResource) {
                        DocumentContentResource documentResource = (DocumentContentResource) resource;
                        setupContentTransfer(request, documentResource);
                    } else {
                        jsonResponseBuilder.success(ctx, new JsonObject());
                    }
                }
            });
        }
    }

    private void setupContentTransfer(HttpServerRequest request,
                                      DocumentContentResource resource) {
        Pump pump = Pump.pump(request, resource.getWriteStream());

        request.endHandler(event -> {
            logger.info("... incoming file transfer completed, {} bytes transferred.",
                ((PumpImpl) pump).getBytesPumped());

            resource.getCloseHandler().handle(null);
        });

        logger.info("incoming file transfer started ...");
        pump.start();

        request.resume();
    }
}
