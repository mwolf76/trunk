package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.handlers.PostResourceRequestHandler;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.resource.impl.ErrorResource;
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

            MultiMap headers = request.headers();
            String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

            Path resolvedPath = storage.getRoot().resolve(protectedPath);
            logger.debug("POST {} -> {} [etag = {}]", protectedPath, resolvedPath, etag);

            storage.putDocumentResource(resolvedPath, resource -> {
                if (resource instanceof ErrorResource) {
                    ErrorResource errorResource = (ErrorResource) resource;
                    if (errorResource.isUnit()) {
                        logger.debug("Ok: {}", ctx.request().uri());
                        jsonResponseBuilder.success(ctx, new JsonObject());
                    } else if (errorResource.isInvalid()) {
                        jsonResponseBuilder.conflict(ctx);
                    } else {
                        logger.error("Error resource should either be unit or invalid");
                        ctx.fail(new RuntimeException("Invalid state"));
                    }
                } else if (resource instanceof CollectionResource) {
                    jsonResponseBuilder.notAcceptable(ctx);
                } else if (resource instanceof DocumentContentResource) {
                    setupContentTransfer(request, (DocumentContentResource) resource);
                }
            });
        }
    }

    private void setupContentTransfer(HttpServerRequest request, DocumentContentResource resource) {
        Pump pump = Pump.pump(request, resource.getWriteStream());

        request.endHandler(event -> {
            logger.info("... incoming file transfer completed, {} bytes transferred.",
                ((PumpImpl) pump).getBytesPumped());

            resource.getCloseHandler()
                .handle(null);
        });

        logger.info("incoming file transfer started ...");
        pump.start();

        request.resume();
    }
}
