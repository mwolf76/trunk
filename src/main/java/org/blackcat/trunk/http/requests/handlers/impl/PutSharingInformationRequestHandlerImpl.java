package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.PutSharingInformationRequestHandler;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.blackcat.trunk.util.Utils.isValidEmail;
import static org.blackcat.trunk.util.Utils.urlDecode;

final public class PutSharingInformationRequestHandlerImpl extends BaseUserRequestHandler
    implements PutSharingInformationRequestHandler {

    final private Logger logger = LoggerFactory.getLogger(PutSharingInformationRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        String requestPathString = urlDecode(ctx.request().path());
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

        Queries.findCreateUserEntityByEmail(ctx.vertx(), email, userMapperAsyncResult -> {
            if (userMapperAsyncResult.failed())
                ctx.fail(userMapperAsyncResult.cause());
            else {
                UserMapper userMapper = userMapperAsyncResult.result();

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
                                Queries.findOrUpdateShareEntity(ctx.vertx(), userMapper, storage.getRoot().relativize(path), newAuthorizedUsers, ar -> {
                                    if (ar.failed())
                                        chainFuture.fail(ar.cause());
                                    else
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
            }
        });
    }
}
