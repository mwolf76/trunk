package org.blackcat.trunk.http;

import com.mitchellbosecke.pebble.utils.Pair;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.streams.Pump;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.eventbus.data.QueryType;
import org.blackcat.trunk.mappers.ShareMapper;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.resource.Resource;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.resource.impl.DocumentDescriptorResource;
import org.blackcat.trunk.resource.impl.ErrorResource;
import org.blackcat.trunk.storage.Storage;
import org.blackcat.trunk.streams.impl.PumpImpl;
import org.blackcat.trunk.util.AsyncInputStream;
import org.blackcat.trunk.util.TarballInputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.blackcat.trunk.util.Utils.*;

public class RequestHandler implements Handler<HttpServerRequest> {

    private Vertx vertx;
    private Router router;
    private TemplateEngine templateEngine;
    private Logger logger;
    private Storage storage;
    private ResponseBuilder responseBuilder;

    public RequestHandler(final Vertx vertx, final TemplateEngine templateEngine,
                          final Logger logger, final Configuration configuration,
                          final Storage storage) {

        this.vertx = vertx;
        this.router = Router.router(vertx);
        this.templateEngine = templateEngine;
        this.logger = logger;
        this.storage = storage;
        this.responseBuilder = new ResponseBuilder(templateEngine, logger);

        // We need cookies, sessions and request bodies
        router.route()
                .handler(CookieHandler.create());

        // avoid reading, sniffing hijacking or tampering your sessions.
        router.route()
                .handler(SessionHandler
                        .create(LocalSessionStore.create(vertx))
                        .setCookieHttpOnlyFlag(true)
                        .setCookieSecureFlag(true));

        // oauth2 setup
        setupOAuth2(vertx, router, configuration);

        /* internal index handler */
        router
                .get("/protected/main")
                .handler(this::main);

        /* share management handlers */
        router
                .getWithRegex("/share/.*")
                .handler(this::getSharingInfo);

        router.routeWithRegex("/share/.*")
                .handler(BodyHandler.create());

        router
                .putWithRegex("/share/.*")
                .handler(BodyHandler.create())
                .handler(this::putSharingInfo);

        /* protected resource handlers */
        router
                .getWithRegex("/protected/.*")
                .handler(this::getResource);

        router
                .putWithRegex("/protected/.*")
                .handler(this::putResource);

        router
                .postWithRegex("/protected/.*")
                .handler(this::postResource);

        router
                .deleteWithRegex("/protected/.*")
                .handler(this::deleteResource);

        /* extra handlers */
        router
                .getWithRegex("/static/.*")
                .handler(StaticHandler.create());

        /* invalid URL */
        router
                .getWithRegex(".*")
                .handler(responseBuilder::notFound);

       /* invalid method */
        router
                .routeWithRegex(".*")
                .handler(responseBuilder::notAllowed);

        /* errors */
        router
                .route()
                .failureHandler(responseBuilder::internalServerError);
    }

    private void setupOAuth2(final Vertx vertx, final Router router, final Configuration configuration) {

        final String callbackURL = "/oauth2";

        OAuth2Auth authProvider = null;
        final String oauth2ProviderName = configuration.getOauth2Provider();
        if (oauth2ProviderName.equals("google")) {
            authProvider = GoogleAuth.create(vertx,
                    configuration.getOauth2ClientID(), configuration.getOauth2ClientSecret());
        }
        if (authProvider == null) {
            throw new RuntimeException(
                    MessageFormat.format("Unsupported OAuth2 provider: {0}",
                            oauth2ProviderName));
        }

        // create a oauth2 handler on our domain
        OAuth2AuthHandler authHandler = OAuth2AuthHandler.create(authProvider,
                configuration.getOAuth2Domain());

        // these are the scopes
        authHandler.addAuthority("profile");
        authHandler.addAuthority("email");

        // We need a user session handler too to make sure the user
        // is stored in the session between requests
        router
                .route()
                .handler(UserSessionHandler.create(authProvider));

        // setup the callback handler for receiving the Google callback
        authHandler.setupCallback(router.get(callbackURL));

        router
                .route("/")
                .handler(this::publicIndexGetRequest);

        // put sharing mgmt under oauth2
        router
                .route("/share/*")
                .handler(authHandler);

        // put protected resource under oauth2
        router
                .route("/protected/*")
                .handler(authHandler);

        // logout
        router
                .route("/logout")
                .handler(ctx -> {
                    User User = ctx.user();
                    AccessToken token = (AccessToken) User;

                    if (token == null) {
                        responseBuilder.found(ctx, "/");
                    }
                    else {
                        // Revoke only the access token
                        token.revoke("access_token", _1 -> {
                            token.revoke("refresh_token", _2 -> {
                                logger.info("Revoked tokens");

                                ctx.clearUser();
                                responseBuilder.found(ctx, "/");
                            });
                        });
                    }
                });

        logger.info("OAUTH2 setup complete");
    }

    @Override
    public void handle(HttpServerRequest request) {
        logger.debug("Accepting HTTP Request: {} {} ...", request.method(), request.uri());
        router.accept(request);
    }

    /*** Route handlers ***********************************************************************************************/
    private void publicIndexGetRequest(RoutingContext ctx) {
        templateEngine.render(ctx, "templates/index", asyncResult -> {
            if (asyncResult.succeeded()) {
                Buffer result = asyncResult.result();
                ctx.response()
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
                        .end(result);
            }
        });
    }

    private void main(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);
        findCreateUserEntityByEmail(email, userMapper -> {
            /* userMapper won't be null */
            String targetURI = userMapper.getUuid() + "/";
            responseBuilder.found(ctx, targetURI);
        });
    }

    private void getSharingInfo(RoutingContext ctx) {

        HttpServerRequest request = ctx.request();
        String requestPathString = urlDecode(request.path());
        Path requestPath = Paths.get(requestPathString);
        Path topLevelPath = Paths.get("/share");
        Path collectionPath = topLevelPath.relativize(requestPath);
        String collectionPathString = collectionPath.toString();

        String email = getSessionUserEmail(ctx);
        findCreateUserEntityByEmail(email, userMapper -> {

            findShareEntity(collectionPath, shareMapper -> {
                JsonArray jsonArray = new JsonArray();

                List<String> authorizedUsers = shareMapper.getAuthorizedUsers();
                for (String authorizedUser : authorizedUsers) {
                    jsonArray.add(authorizedUser);
                }

                String body = new JsonObject()
                        .put("data", new JsonObject()
                                .put("collectionPath", collectionPathString)
                                .put("authorizedUsers", jsonArray))
                        .encodePrettily();

                ctx.response()
                        .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                        .end(body);
            });
        });
    }

    private void putSharingInfo(RoutingContext ctx) {

        HttpServerRequest request = ctx.request();

        String requestPathString = urlDecode(request.path());
        Path requestPath = Paths.get(requestPathString);

        Path topLevelPath = Paths.get("/share");
        Path collectionPath = topLevelPath.relativize(requestPath);

        JsonObject json = ctx.getBodyAsJson();
        if (json == null) {
            responseBuilder.badRequest(ctx);
            return;
        }

        /* TODO: review this */
        JsonArray authorizedUsers = null;
        try {
            authorizedUsers = json.getJsonArray("authorizedUsers");
        } catch (ClassCastException cce) {
        }
        List<String> newAuthorizedUsers = new ArrayList<>();
        for (Object o : authorizedUsers) {
            if (o instanceof String) {
                String email = (String) o;
                if (email.equals("*") || isValidEmail(email)) {
                    newAuthorizedUsers.add(email);
                }
            }
        }

        User User = ctx.user();
        AccessToken at = (AccessToken) User;

        JsonObject idToken = KeycloakHelper.idToken(at.principal());
        String email = idToken.getString("email");

        findCreateUserEntityByEmail(email, userMapper -> {

            vertx.executeBlocking(future -> {
                try (Stream<Path> pathStream = storage.streamDirectory(storage.getRoot().resolve(collectionPath))) {
                    future.complete(pathStream.collect(Collectors.toList()));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }, res -> {
                final List<Path> paths = (List<Path>) res.result();
                if (paths == null) {
                    responseBuilder.internalServerError(ctx);
                    return;
                }

                /* first link of the chain */
                Future<Void> initFuture = Future.future(event -> {
                    logger.info("Started updating share permissions, owner is {}.", userMapper.getEmail());
                    responseBuilder.done(ctx); /* return control to the user, process will continue in background */
                });
                Future<Void> prevFuture = initFuture;

                for (Path path : paths) {
                    Future chainFuture = Future.future();
                    prevFuture.compose(v -> {
                        if (userMapper != null && collectionPath.startsWith(Paths.get(userMapper.getUuid()))) {
                            findOrUpdateShareEntity(userMapper, storage.getRoot().relativize(path), newAuthorizedUsers, done -> {
                                chainFuture.complete();
                            });
                        } else {
                            chainFuture.fail("No bloody way!");
                        }
                    }, chainFuture);

                    prevFuture = chainFuture;
                }
                prevFuture.compose(v -> {
                    logger.info("Done updating share permissions ({} entries processed).", paths.size());
                }, initFuture);

                /* let's get this thing started ... */
                initFuture.complete();
            });
        });
    } /* putSharingInfo() */

    private void getResource(RoutingContext ctx) {
        String email = getSessionUserEmail(ctx);
        Path protectedPath = protectedPath(ctx);

        findCreateUserEntityByEmail(email, userMapper -> {

            /* check for ownership */
            if (protectedPath.startsWith(Paths.get(userMapper.getUuid()))) {
                logger.debug("Ownership granted to user {}. No further auth checks required.",
                        userMapper.getEmail());

                findShareEntity(protectedPath, shareMapper -> {
                    getResourceAux(ctx, true, shareMapper);
                });
            } else {
                /* not the owner, authorized? */
                findShareEntity(protectedPath, shareMapper -> {

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

        MultiMap headers = request.headers();
        String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.trace("GET {} -> {} [etag: {}]",
                protectedPath.toString(), resolvedPath.toString(), etag);

        storage.get(resolvedPath, etag, resource -> {
            /* Error handling */
            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;
                if (errorResource.isNotFound()) {
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
                            .put("userEmail", getSessionUserEmail(ctx))
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
                            .put("collectionTitle", String.format("trunk (%s)", getSessionUserEmail(ctx)))
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

    /* PUTs are used to create collections */
    private void putResource(RoutingContext ctx) {

        HttpServerRequest request = ctx.request();
        Path protectedPath = protectedPath(ctx);

        MultiMap headers = request.headers();
        String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.trace("PUT {} -> {} [etag = {}]", protectedPath, resolvedPath, etag);

        storage.putCollection(resolvedPath, etag, resource -> {
            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;
                if (errorResource.isUnit()) {
                    responseBuilder.ok(ctx);
                } else if (errorResource.isInvalid()) {
                    responseBuilder.conflict(ctx, errorResource.getMessage());
                } else {
                    responseBuilder.internalServerError(ctx);
                }
                return;
            }
        });
    } /* putResource() */

    /* POSTs are used to create/update documents */
    private void postResource(RoutingContext ctx) {

        final HttpServerRequest request = ctx
                .request()
                .pause(); /* hold it */

        Path protectedPath = protectedPath(ctx);

        MultiMap headers = request.headers();
        String etag = headers.get(Headers.IF_NONE_MATCH_HEADER);

        Path resolvedPath = storage.getRoot().resolve(protectedPath);
        logger.trace("POST {} -> {} [etag = {}]", protectedPath, resolvedPath, etag);

        storage.putDocument(resolvedPath, etag, resource -> {

            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;
                if (errorResource.isUnit()) {
                    responseBuilder.ok(ctx);
                }
                else if (errorResource.isInvalid()) {
                    responseBuilder.conflict(ctx, errorResource.getMessage());
                } else {
                    responseBuilder.internalServerError(ctx);
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

                /* setting up xfer */
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

    private void deleteResource(RoutingContext ctx) {

        final HttpServerRequest request = ctx.request();
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

    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email
     * @param handler
     */
    private void findCreateUserEntityByEmail(String email, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", QueryType.FIND_CREATE_USER.getTag())
                .put("params", new JsonObject()
                        .put("email", email));

        vertx.eventBus()
            .send("data-store", query, reply -> {
                if (reply.failed())
                    handler.handle(null);
                else {
                    JsonObject obj = (JsonObject) reply.result().body();
                    handler.handle(obj.mapTo(UserMapper.class));
                }
        });
    }

    /**
     * Updates the sharing entity corresponding to collectionPath if such entity exists.
     * Creates a new one otherwise.
     *
     * @param owner
     * @param collectionPath
     * @param authorizedUsers
     * @param handler
     */
    private void findOrUpdateShareEntity(UserMapper owner, Path collectionPath,
                                         List<String> authorizedUsers, Handler<ShareMapper> handler) {

        JsonObject query = new JsonObject()
                .put("type", QueryType.FIND_UPDATE_SHARE.getTag())
                .put("params", new JsonObject()
                        .put("owner", JsonObject.mapFrom(owner))
                        .put("collectionPath", collectionPath.toString())
                        .put("authorizedUsers", new JsonArray(authorizedUsers)));

        vertx.eventBus()
            .send("data-store", query, reply -> {
                if (reply.failed())
                    handler.handle(null);
                else {
                    JsonObject obj = (JsonObject) reply.result().body();
                    handler.handle(obj.mapTo(ShareMapper.class));
                }
        });

    } /* findOrUpdateShareEntity() */

    /**
     * Fetches an existing share entity related to collectionPath or NULL if no such entity exists.
     *
     * @param collectionPath
     * @param handler
     */
    private void findShareEntity(Path collectionPath, Handler<ShareMapper> handler) {
        JsonObject query = new JsonObject()
                .put("type", QueryType.FIND_SHARE.getTag())
                .put("params", new JsonObject()
                        .put("collectionPath", collectionPath.toString()));

        vertx.eventBus()
            .send("data-store", query, reply -> {
            if (reply.failed())
                handler.handle(null);
            else {
                JsonObject obj = (JsonObject) reply.result().body();
                handler.handle(obj.mapTo(ShareMapper.class));
            }
        });
    } /* findShareEntity() */

    /*** Helpers ******************************************************************************************************/
    private static String getSessionUserEmail(RoutingContext ctx) {
        User User = ctx.user();
        AccessToken at = (AccessToken) User;

        JsonObject idToken = KeycloakHelper.idToken(at.principal());
        String email = idToken.getString("email");

        return email;
    }

    private static String buildBackLink(int index) {
        StringBuilder sb = new StringBuilder();

        sb.append("./");
        for (int i = 0; i < index; ++ i)
            sb.append("../");

        return sb.toString();
    }

    private static String chopString(String s) {
        return s.substring(0, s.length() -2);
    }

    private static Path protectedPath(RoutingContext ctx) {
        Path prefix = Paths.get("/protected");
        return prefix.relativize(Paths.get(urlDecode(ctx.request().path())));
    }
}
