package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
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
        HttpServerRequest request = ctx.request();
        Path protectedPath = protectedPath(ctx);

        MultiMap headers = request.headers();
        String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.debug("POST {} -> {} [etag = {}]", protectedPath, resolvedPath, etag);

        storage.putDocument(resolvedPath, etag, resource -> {

            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;
                if (errorResource.isUnit()) {
                    responseBuilder.ok(ctx);
                }
                else if (errorResource.isInvalid()) {
                    responseBuilder.conflict(ctx, errorResource.getMessage());
                } else {
                    logger.error("Error resource should either be unit or invalid");
                    ctx.fail(new RuntimeException("Invalid state"));
                }
            }

            else if (! resource.isModified()) {
                responseBuilder.notModified(ctx, etag);
            }

            else if (resource instanceof CollectionResource) {
                responseBuilder.notAcceptable(ctx);
            }

            else if (resource instanceof DocumentContentResource) {
                DocumentContentResource documentContentResource =
                    (DocumentContentResource) resource;

                Pump pump = Pump.pump(request, documentContentResource.getWriteStream());

                request.endHandler(event -> {
                    logger.info("... incoming file transfer completed, {} bytes transferred.",
                        ((PumpImpl) pump).getBytesPumped());

                    documentContentResource.getCloseHandler()
                        .handle(null);
                });

                logger.info("incoming file transfer started ...");
                pump.start();

                request.resume();
            }
        });

    }
}
