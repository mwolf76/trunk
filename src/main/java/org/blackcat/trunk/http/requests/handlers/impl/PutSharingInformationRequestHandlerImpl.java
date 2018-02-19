package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.PutSharingInformationRequestHandler;
import org.blackcat.trunk.http.requests.response.ResponseUtils;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        checkJsonRequest(ctx, ok -> {
            Path collectionPath = collectionPath(ctx);
            Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed())
                    ctx.fail(userMapperAsyncResult.cause());
                else {
                    UserMapper userMapper = userMapperAsyncResult.result();
                    if (userOwnsThisCollection(userMapper, collectionPath)) {
                        asyncRewriteCollectionShareInfo(ctx, collectionPath, userMapper);
                    } else jsonResponseBuilder.methodNotAllowed(ctx);
                }
            });
        });
    }

    private void asyncRewriteCollectionShareInfo(RoutingContext ctx, Path collectionPath, UserMapper userMapper) {
        vertx.executeBlocking((Future<List<Path>> future) -> {
            future.complete(pathStream(collectionPath).collect(Collectors.toList()));
        }, asyncResult -> {
            if (asyncResult.failed()) {
                ctx.fail(asyncResult.cause());
            } else {
                setupRewriteTaskChain(ctx, userMapper, newAuthorizedUsers(ctx), asyncResult.result());
            }
        });
    }

    private Stream<Path> pathStream(Path collectionPath) {
        return storage.streamDirectory(storage.getRoot().resolve(collectionPath));
    }

    private boolean userOwnsThisCollection(UserMapper userMapper, Path collectionPath) {
        return collectionPath.startsWith(Paths.get(userMapper.getUuid()));
    }

    private Path collectionPath(RoutingContext ctx) {
        Path requestPath = Paths.get(urlDecode(ctx.request().path()));
        Path topLevelPath = Paths.get("/share");
        return topLevelPath.relativize(requestPath);
    }

    private void setupRewriteTaskChain(RoutingContext ctx, UserMapper userMapper,
                                       List<String> newAuthorizedUsers, List<Path> paths) {

        /* first link of the chain */
        Future<Void> initFuture = Future.future(invoked -> {
            logger.info("Started updating share permissions, owner is {}.", userMapper.getEmail());
            jsonResponseBuilder.success(ctx, new JsonObject()); /* return control to the user, process will continue in background */
        });

        /* put together intermediate links of the chain (actual tasks) */
        Future<Void> prevFuture = initFuture;
        for (Path path : paths) {
            Future chainFuture = Future.<Void> future();
            rewriteTask(ctx, userMapper, path, newAuthorizedUsers, prevFuture, chainFuture);
            prevFuture = chainFuture;
        }

        /* last link of the chain */
        prevFuture.compose(invoked -> {
            logger.info("Done updating share permissions ({} entries processed).", paths.size());
        }, initFuture);

        /* let's get this thing started ... */
        initFuture.complete();
    }

    private Future rewriteTask(RoutingContext ctx, UserMapper userMapper,
                               Path path, List<String> newAuthorizedUsers,
                               Future<Void> prevFuture, Future chainFuture) {
        return prevFuture.compose(invoked -> {
            Queries.findUpdateShareEntity(ctx.vertx(), userMapper,
                relativePath(path), newAuthorizedUsers, ar -> {
                    if (ar.failed()) {
                        logger.error("Failed to rewrite sharing information for {}", path);
                        chainFuture.fail(ar.cause());
                    } else {
                        logger.debug("Rewrote sharing information for {}", path);
                        chainFuture.complete();
                    }
                });
        }, chainFuture);
    }

    private Path relativePath(Path path) {
        return storage.getRoot().relativize(path);
    }

    private List<String> newAuthorizedUsers(RoutingContext ctx) {
        JsonObject bodyAsJson = ctx.getBodyAsJson();
        JsonArray authorizedUsers = bodyAsJson.getJsonArray("authorizedUsers");
        return authorizedUsers.stream()
            .filter(o -> o instanceof String)
            .map(String::valueOf)
            .filter(s -> "*".equals(s) || isValidEmail(s))
            .collect(Collectors.toList());
    }
}
