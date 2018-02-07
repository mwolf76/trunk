package org.blackcat.trunk.http.requests.impl;

import com.mitchellbosecke.pebble.utils.Pair;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.GetResourceRequestHandler;
import org.blackcat.trunk.mappers.ShareMapper;
import org.blackcat.trunk.queries.Queries;
import org.blackcat.trunk.resource.Resource;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.resource.impl.DocumentDescriptorResource;
import org.blackcat.trunk.resource.impl.ErrorResource;
import org.blackcat.trunk.streams.impl.PumpImpl;
import org.blackcat.trunk.util.AsyncInputStream;
import org.blackcat.trunk.util.TarballInputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.blackcat.trunk.util.Utils.*;

final public class GetResourceRequestHandlerImpl extends BaseUserRequestHandler implements GetResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(GetResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        ctx.request().resume();

        Path protectedPath = protectedPath(ctx);

        logger.info("Getting resource {} -> {}", ctx.request().path(), protectedPath);
        Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapper -> {

            /* check for ownership */
            if (protectedPath.startsWith(Paths.get(userMapper.getUuid()))) {
                logger.info("Ownership granted to user {}. No further auth checks required.",
                    userMapper.getEmail());

                Queries.findShareEntity(ctx.vertx(), protectedPath, shareMapper -> {
                    getResourceAux(ctx, true, shareMapper);
                });
            } else {
                /* not the owner, authorized? */
                Queries.findShareEntity(ctx.vertx(), protectedPath, shareMapper -> {

                    if (shareMapper.isAuthorized(userMapper.getEmail())) {

                        logger.info("Auth granted by sharing permissions to user {}.",
                            userMapper.getEmail());

                        getResourceAux(ctx, false, shareMapper);
                    }
                    else {
                        logger.warn("Not the owner, nor sharing permission exists for user {}. Access denied.",
                            userMapper.getEmail());

                        responseBuilder.forbidden(ctx);
                    }
                });
            }
        });
    }

    private void getResourceAux(RoutingContext ctx, boolean isOwner, ShareMapper shareMapper) {
        HttpServerRequest request = ctx.request();

        Path protectedPath = protectedPath(ctx);
        boolean trailingSlash = urlDecode(request.path()).endsWith("/");
        boolean toplevel = protectedPath.getNameCount() == 1;

        MultiMap headers = request.headers();
        String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.trace("GET {} -> {} [etag: {}]",
            protectedPath.toString(), resolvedPath.toString(), etag);

        storage.get(resolvedPath, etag, resource -> {

            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;

                // FIXME: 2/3/18 eliminate switch
                if (errorResource.isNotFound()) {
                    logger.debug("Resource not found: {}", request.uri());

                    if (isOwner && trailingSlash && toplevel) {
                        logger.debug("Creating new collection: {}", request.uri());
                        storage.putCollection(resolvedPath, etag, newResource -> {
                            responseBuilder.found(ctx, request.uri());
                        });
                        return;
                    }

                    responseBuilder.notFound(ctx);
                    return;
                } else {
                    responseBuilder.internalServerError(ctx);
                    return;
                }
            }

            /* Cache handling */
            if (! resource.isModified()) {
                responseBuilder.notModified(ctx, etag);
                return;
            }

            String accept = headers.get(Headers.ACCEPT_HEADER);
            boolean html = (accept != null && accept.contains("text/html"));
            boolean json = (accept != null && accept.contains("application/json"));

            /* URL params */
            boolean tarball = "t".equals(request.getParam("tarball"));

            /* Collection resources handling */
            if (resource instanceof CollectionResource) {
                CollectionResource collection = (CollectionResource) resource;

                /* URL missing trailing '/'? Redirect to proper URL */
                if (! trailingSlash) {
                    responseBuilder.found(ctx, ctx.request().path() + "/");
                    return;
                }

                if (tarball) {
                    try {
                        TarballInputStream tarballInputStream =
                            new TarballInputStream(storage, resolvedPath);

                        AsyncInputStream asyncInputStream = new AsyncInputStream(
                            vertx, tarballInputStream);

                        String archiveName = resolvedPath.getFileName().toString() + ".tar";

                        ctx.response()
                            .putHeader(Headers.CONTENT_TYPE_HEADER, "application/x-tar")
                            .putHeader(Headers.CONTENT_DISPOSITION, String.format(
                                "attachment; filename=\"%s\"", archiveName))
                            .setChunked(true) // required
                        ;

                        asyncInputStream.exceptionHandler( exception -> {
                            logger.error(exception.toString());
                        });

                        /* setting up xfer */
                        Pump pump = Pump.pump(asyncInputStream, ctx.response());

                        /* when all is done on the destination stream, report stats and close the response. */
                        asyncInputStream
                            .exceptionHandler(cause -> {
                                logger.error(cause.toString());
                            })
                            .endHandler(event -> {
                                pump.stop();
                                logger.info("... archive file transfer completed, {} bytes transferred.",
                                    ((PumpImpl) pump).getBytesPumped());

                                responseBuilder.done(ctx);
                            });

                        ctx
                            .response()
                            .closeHandler(event -> {
                                logger.info("interrupted by client");
                                tarballInputStream.setCanceled(true);
                            });

                        logger.info("archive file transfer started for {} ...", archiveName);
                        pump.start();
                    }
                    catch (IOException ioe) {
                        logger.error(ioe.toString());
                        responseBuilder.internalServerError(ctx);
                    }
                }

                else if (html) {
                    List<Pair<String, String>> frags = new ArrayList<>();

                    protectedPath
                        .forEach(new Consumer<Path>() {
                            /* stateful functor */
                            int depth = protectedPath.getNameCount();

                            @Override
                            public void accept(Path path) {
                                frags.add(new Pair<>(buildBackLink(-- depth),
                                    depth == protectedPath.getNameCount() - 1 /* first */
                                        ? null : path.toString()));
                            }
                        });

                    boolean isToplevel = protectedPath.getNameCount() == 1;

                    ctx
                        .put("userEmail", ctx.<String>get("email"))
                        .put("isOwner", isOwner)
                        .put("isToplevel", isToplevel)
                        .put("isEmpty", collection.getItems().size() == 0);

                    if (isOwner) {
                        ctx
                            .put("authorizedUsers",
                                String.join("; ", shareMapper.getAuthorizedUsers()));
                    }
                    else {
                        ctx
                            .put("ownerEmail", shareMapper.getOwner().getEmail());
                    }

                    ctx
                        .put("collectionTitle", String.format("trunk (%s)", ctx.<String>get("email")))
                        .put("collectionPath", protectedPath)
                        .put("pathFragments", frags)

                        .put("entries", collection.getItems());

                    // and now delegate to the engine to render it.
                    templateEngine.render(ctx, "templates/collection", asyncResult -> {
                        if (asyncResult.succeeded()) {
                            ctx.response()
                                .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                                .end(asyncResult.result());
                        } else {
                            ctx.fail(asyncResult.cause());
                        }
                    });
                }

                /* json */
                else if (json) {
                    JsonArray entries = new JsonArray();
                    for (Resource res : collection.getItems()) {
                        if (res instanceof CollectionResource) {
                            final CollectionResource collectionResource = (CollectionResource) res;
                            entries.add(new JsonObject()
                                            .put("href", request.absoluteURI() + urlEncode(collectionResource.getName()) + "/")
                                            .put("name", collectionResource.getName())
                                            .put("class", "collection")
                                            .put("count", Integer.valueOf(collectionResource.getSize()).toString()));
                        } else if (res instanceof DocumentDescriptorResource) {
                            final DocumentDescriptorResource documentDescriptorResource = (DocumentDescriptorResource) res;
                            entries.add(new JsonObject()
                                            .put("href", request.absoluteURI() + urlEncode(documentDescriptorResource.getName()))
                                            .put("name", documentDescriptorResource.getName())
                                            .put("mimeType", documentDescriptorResource.getMimeType())
                                            .put("class", "document")
                                            .put("created", documentDescriptorResource.getHumanCreationTime())
                                            .put("modified", documentDescriptorResource.getHumanLastModificationTime())
                                            .put("accessed", documentDescriptorResource.getHumanLastAccessedTime())
                                            .put("length", documentDescriptorResource.getHumanLength()));
                        } else responseBuilder.internalServerError(ctx);
                    }

                    final String body =
                        new JsonObject()
                            .put("data", new JsonObject()
                                             .put(protectedPath.getFileName().toString(), entries))
                            .encodePrettily();

                    ctx.response()
                        .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                        .end(body);
                }

                /* how exactly am I supposed to talk to you? */
                // TODO: review this
                else responseBuilder.notAcceptable(ctx);
            }

            else if (resource instanceof DocumentDescriptorResource) {
                DocumentDescriptorResource documentDescriptorResource =
                    (DocumentDescriptorResource) resource;

                String body =
                    new JsonObject()
                        .put("meta", new JsonObject()
                                         .put("href", request.absoluteURI() +
                                                          urlEncode(documentDescriptorResource.getName() + "/meta"))
                                         .put("name", documentDescriptorResource.getName())
                                         .put("mimeType", documentDescriptorResource.getMimeType())
                                         .put("class", "document")
                                         .put("created", documentDescriptorResource.getCreationTime())
                                         .put("modified", documentDescriptorResource.getLastModificationTime())
                                         .put("accessed", documentDescriptorResource.getLastAccessedTime())
                                         .put("length", documentDescriptorResource.getLength())
                        )
                        .encodePrettily();

                ctx.response()
                    .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                    .end(body);
            }

            /* Document resources handling */
            else if (resource instanceof DocumentContentResource) {

                /* extra trailing '/'? Redirect */
                if (trailingSlash) {
                    responseBuilder.found(ctx, chopString(ctx.request().path()));
                    return;
                } else {
                    DocumentContentResource documentContentResource =
                        (DocumentContentResource) resource;

                    String documentResourceEtag = documentContentResource.getEtag();
                    if (documentResourceEtag != null && !documentResourceEtag.isEmpty()) {
                        ctx.response()
                            .putHeader(Headers.ETAG_HEADER, documentResourceEtag);
                    }

                    /* setting up xfer */
                    ctx.response()
                        .putHeader(Headers.CONTENT_TYPE_HEADER,
                            documentContentResource.getMimeType())
                        .putHeader(Headers.CONTENT_LENGTH_HEADER,
                            String.valueOf(documentContentResource.getLength()));

                    Pump pump = Pump.pump(documentContentResource.getReadStream(), ctx.response(), 8192);

                    /* when all is done on the source stream, report stats and close the response. */
                    documentContentResource.getReadStream()
                        .endHandler(event -> {
                            pump.stop();
                            logger.info("... outgoing file transfer completed, {} bytes transferred.",
                                ((PumpImpl) pump).getBytesPumped());

                            documentContentResource.getCloseHandler()
                                .handle(null);

                            responseBuilder.done(ctx);
                        });

                    logger.info("outgoing file transfer started ...");
                    pump.start();
                }
            }
        });
    }
}
