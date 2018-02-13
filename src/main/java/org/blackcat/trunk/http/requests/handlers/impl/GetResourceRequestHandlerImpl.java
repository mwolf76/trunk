package org.blackcat.trunk.http.requests.handlers.impl;

import com.mitchellbosecke.pebble.utils.Pair;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.handlers.GetResourceRequestHandler;
import org.blackcat.trunk.http.requests.response.ResponseUtils;
import org.blackcat.trunk.mappers.ShareMapper;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;
import org.blackcat.trunk.resource.Resource;
import org.blackcat.trunk.resource.exceptions.NotFoundException;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.resource.impl.DocumentDescriptorResource;
import org.blackcat.trunk.streams.impl.PumpImpl;
import org.blackcat.trunk.util.AsyncInputStream;
import org.blackcat.trunk.util.TarballInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.blackcat.trunk.util.Utils.*;

final public class GetResourceRequestHandlerImpl extends BaseUserRequestHandler implements GetResourceRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(GetResourceRequestHandlerImpl.class);

    final static String tarMimeType = "application/x-tar";

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapperAsyncResult -> {

            if (userMapperAsyncResult.failed()) {
                ctx.fail(userMapperAsyncResult.cause());
            }
            else {
                UserMapper userMapper = userMapperAsyncResult.result();

                Path protectedPath = protectedPath(ctx);
                logger.info("Getting resource {}", protectedPath);

                if (userHasOwnership(userMapper, protectedPath)) {
                    logger.debug("Ownership granted to user {}. No further auth checks required.",
                        userMapper.getEmail());

                    Queries.findShareEntity(ctx.vertx(), protectedPath, shareMapperAsyncResult -> {
                        if (shareMapperAsyncResult.failed())
                            ctx.fail(shareMapperAsyncResult.cause());
                        else {
                            ShareMapper shareMapper = shareMapperAsyncResult.result();
                            getResourceAux(ctx, shareMapper, true);
                        }
                    });
                } else {
                    /* not the owner, authorized? */
                    Queries.findShareEntity(ctx.vertx(), protectedPath, shareMapperAsyncResult -> {

                        if (shareMapperAsyncResult.failed())
                            ctx.fail(shareMapperAsyncResult.cause());
                        else {
                            ShareMapper shareMapper = shareMapperAsyncResult.result();
                            if (shareMapper.isAuthorized(userMapper.getEmail())) {

                                logger.debug("Auth granted by sharing permissions to user {}.",
                                    userMapper.getEmail());

                                getResourceAux(ctx, shareMapper, false);
                            } else {
                                logger.warn("Not the owner, nor sharing permission exists for user {}. Access denied.",
                                    userMapper.getEmail());

                                htmlResponseBuilder.forbidden(ctx);
                            }
                        }
                    });
                }
            }
        });
    }

    private boolean userHasOwnership(UserMapper userMapper, Path protectedPath) {
        return protectedPath.startsWith(Paths.get(userMapper.getUuid()));
    }

    private void getResourceAux(RoutingContext ctx, ShareMapper shareMapper, boolean isOwner) {
        Path protectedPath = protectedPath(ctx);
        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.trace("GET {} -> {}", protectedPath.toString(), resolvedPath.toString());

        storage.get(resolvedPath, resourceAsyncResult -> {
            if (resourceAsyncResult.failed()) {
                Throwable cause = resourceAsyncResult.cause();
                if (cause instanceof NotFoundException) {
                    logger.warn("Resource not found: {}", ctx.request().uri());
                    htmlResponseBuilder.notFound(ctx);
                } else {
                    logger.warn("Could not retrieve resource {}: {}", ctx.request().uri(), cause);
                    htmlResponseBuilder.badRequest(ctx);
                }
            } else {
                Resource resource = resourceAsyncResult.result();
                if (resource instanceof CollectionResource) {
                    collectionResourceResponse(ctx, (CollectionResource) resource, resolvedPath, shareMapper, isOwner);
                } else if (resource instanceof DocumentDescriptorResource) {
                    documentDescriptorResponse(ctx, (DocumentDescriptorResource) resource);
                } else if (resource instanceof DocumentContentResource) {
                    documentContentResponse(ctx, (DocumentContentResource) resource);
                }
            }
        });
    }

    private void collectionResourceResponse(RoutingContext ctx, CollectionResource resource,
                                            Path resolvedPath, ShareMapper shareMapper, boolean isOwner) {
        if (! forceTrailingSlash(ctx)) {
            HttpServerRequest request = ctx.request();
            Path protectedPath = protectedPath(ctx);

            if ("t".equals(request.getParam("tarball"))) {
                collectionTarballResponse(ctx, resolvedPath);
            } else if (ctx.get("requestType").equals(RequestType.JSON)) {
                collectionJsonResponse(ctx, protectedPath, resource);
            } else {
                collectionHtmlResponse(ctx, shareMapper, protectedPath, resource, isOwner);
            }
        }
    }

    private void collectionJsonResponse(RoutingContext ctx,
                                        Path protectedPath, CollectionResource collection) {
        HttpServerRequest request = ctx.request();
        List<JsonObject> entries =
            collection.getItems().stream()
                .filter(this::isCollectionOrDocumentDescriptor)
                .map(res -> {
                    if (res instanceof CollectionResource)
                        return collectionResourceJsonObject(request, res);
                    else
                        return documentDescriptorResourceJsonObject(request, res);
                }).collect(Collectors.toList());

        jsonResponseBuilder.success(ctx, new JsonObject()
                                             .put("data", new JsonObject()
                                                              .put(protectedPath.getFileName().toString(), entries)));
    }

    private boolean isCollectionOrDocumentDescriptor(Resource resource) {
        return resource instanceof CollectionResource || resource instanceof DocumentDescriptorResource ;
    }

    private JsonObject documentDescriptorResourceJsonObject(HttpServerRequest request, Resource res) {
        DocumentDescriptorResource documentDescriptorResource = (DocumentDescriptorResource) res;
        return new JsonObject()
                   .put("href", documentAbsoluteURI(request, documentDescriptorResource))
                   .put("name", documentDescriptorResource.getName())
                   .put("mimeType", documentDescriptorResource.getMimeType())
                   .put("class", "document")
                   .put("created", documentDescriptorResource.getHumanCreationTime())
                   .put("modified", documentDescriptorResource.getHumanLastModificationTime())
                   .put("accessed", documentDescriptorResource.getHumanLastAccessedTime())
                   .put("length", documentDescriptorResource.getHumanLength());
    }

    private JsonObject collectionResourceJsonObject(HttpServerRequest request, Resource res) {
        CollectionResource collectionResource = (CollectionResource) res;
        return new JsonObject()
                   .put("href", collectionAbsoluteURI(request, collectionResource))
                   .put("name", collectionResource.getName())
                   .put("class", "collection")
                   .put("count", Integer.valueOf(collectionResource.getSize()).toString());
    }

    @NotNull
    private String documentAbsoluteURI(HttpServerRequest request, DocumentDescriptorResource documentDescriptorResource) {
        return request.absoluteURI() + urlEncode(documentDescriptorResource.getName());
    }

    @NotNull
    private String collectionAbsoluteURI(HttpServerRequest request, CollectionResource collectionResource) {
        return request.absoluteURI() + urlEncode(collectionResource.getName()) + "/";
    }

    private void collectionHtmlResponse(RoutingContext ctx, ShareMapper shareMapper,
                                        Path protectedPath, CollectionResource collection, boolean isOwner) {
        List<Pair<String, String>> frags = new ArrayList<>();

        protectedPath
            .forEach(new Consumer<Path>() {
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
            ctx.put("authorizedUsers",
                String.join("; ", shareMapper.getAuthorizedUsers()));
        }
        else {
            ctx.put("ownerEmail", shareMapper.getOwner().getEmail());
        }

        ctx
            .put("collectionTitle", collectionTitle(ctx))
            .put("collectionPath", protectedPath)
            .put("pathFragments", frags)
            .put("entries", collection.getItems());

        htmlResponseBuilder.success(ctx, "collection");
    }

    private String collectionTitle(RoutingContext ctx) {
        return String.format("trunk (%s)", ctx.<String>get("email"));
    }

    private void collectionTarballResponse(RoutingContext ctx, Path resolvedPath) {
        try {
            TarballInputStream tarballInputStream =
                new TarballInputStream(storage, resolvedPath);

            String archiveName = resolvedPath.getFileName().toString() + ".tar";

            ctx.response()
                .putHeader(Headers.CONTENT_TYPE_HEADER, tarMimeType)
                .putHeader(Headers.CONTENT_DISPOSITION, String.format("attachment; filename=\"%s\"", archiveName))
                .setChunked(true) // required
            ;

            setupTarballTransfer(ctx, tarballInputStream, archiveName);
        }
        catch (IOException ioe) {
            logger.error(ioe);
            ctx.fail(ioe);
        }
    }

    private void setupTarballTransfer(RoutingContext ctx, TarballInputStream tarballInputStream,
                                      String archiveName) {

        AsyncInputStream asyncInputStream = new AsyncInputStream(
            vertx, tarballInputStream);

        asyncInputStream.exceptionHandler( exception -> {
            logger.error(exception.toString());
            ctx.fail(exception);
        });

        Pump pump = Pump.pump(asyncInputStream, ctx.response());

        /* when all is done on the destination stream, report stats and close the response. */
        asyncInputStream
            .exceptionHandler(cause -> {
                logger.error(cause.toString());
            })
            .endHandler(event -> {
                pump.stop();
                logger.debug("... archive file transfer completed, {} bytes transferred.",
                    ((PumpImpl) pump).getBytesPumped());

                ResponseUtils.complete(ctx);
            });

        ctx
            .response()
            .closeHandler(event -> {
                logger.warn("interrupted by client");
                tarballInputStream.setCanceled(true);
            });

        logger.debug("archive file transfer started for {} ...", archiveName);
        pump.start();
    }

    private void documentDescriptorResponse(RoutingContext ctx, DocumentDescriptorResource resource) {
        checkJsonRequest(ctx, ok -> {
            jsonResponseBuilder.success(ctx,
                new JsonObject()
                    .put("meta", new JsonObject()
                                     .put("href", metaResourceURL(ctx, resource))
                                     .put("name", resource.getName())
                                     .put("mimeType", resource.getMimeType())
                                     .put("class", "document")
                                     .put("created", resource.getCreationTime())
                                     .put("modified", resource.getLastModificationTime())
                                     .put("accessed", resource.getLastAccessedTime())
                                     .put("length", resource.getLength())));
        });
    }

    @NotNull
    private String metaResourceURL(RoutingContext ctx, DocumentDescriptorResource resource) {
        return ctx.request().absoluteURI() +
            urlEncode(resource.getName() + "/meta");
    }

    private void documentContentResponse(RoutingContext ctx, DocumentContentResource resource) {
        if (! forceNoTrailingSlash(ctx)) {
            HttpServerResponse response = ctx.response();

            response
                .putHeader(Headers.CONTENT_TYPE_HEADER, resource.getMimeType())
                .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(resource.getLength()));

            Pump pump = Pump.pump(resource.getReadStream(), response, 8192);

            /* when all is done on the source stream, report stats and close the response. */
            resource.getReadStream()
                .endHandler(event -> {
                    pump.stop();
                    logger.info("... outgoing file transfer completed, {} bytes transferred.",
                        ((PumpImpl) pump).getBytesPumped());

                    resource.getCloseHandler()
                        .handle(null);

                    ResponseUtils.complete(ctx);
                });

            logger.debug("outgoing file transfer started ...");
            pump.start();
        }
    }

    private boolean forceNoTrailingSlash(RoutingContext ctx) {
        boolean res = hasTrailingSlash(ctx);
        if (res) {
            String uri = chopString(ctx.request().path());
            logger.debug("Extra trailing slash. Redirecting to {} ...", uri);
            ResponseUtils.found(ctx, uri);
        }

        return res;
    }

    private boolean forceTrailingSlash(RoutingContext ctx) {
        boolean res = ! hasTrailingSlash(ctx);
        if (res) {
            String uri = ctx.request().path() + "/";
            logger.debug("No trailing slash. Redirecting to {} ...", uri);
            ResponseUtils.found(ctx, uri);
        }

        return res;
    }

    private boolean hasTrailingSlash(RoutingContext ctx) {
        return urlDecode(ctx.request().path()).endsWith("/");
    }
}
