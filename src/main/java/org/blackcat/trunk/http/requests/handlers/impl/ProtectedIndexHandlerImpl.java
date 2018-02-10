package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.ProtectedIndexHandler;
import org.blackcat.trunk.http.requests.response.ResponseUtils;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.ErrorResource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static org.blackcat.trunk.util.Utils.protectedPath;

final public class ProtectedIndexHandlerImpl extends BaseUserRequestHandler implements ProtectedIndexHandler {

    final private Logger logger = LoggerFactory.getLogger(ProtectedIndexHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        if (ctx.get("requestType").equals(RequestType.JSON))
            htmlResponseBuilder.badRequest(ctx);
        else {
            Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed())
                    ctx.fail(userMapperAsyncResult.cause());
                else {
                    UserMapper userMapper = userMapperAsyncResult.result();
                    ensureUserRootCollectionExists(ctx, userMapper, asyncResult -> {
                        if (asyncResult.failed())
                            ctx.fail(asyncResult.cause());
                        else {
                            String targetURI = userMapper.getUuid() + "/";
                            logger.debug("Redirecting to user's protected contents {} ...", targetURI);
                            ResponseUtils.found(ctx, targetURI);
                        }
                    });
                }
            });
        }
    }

    private void ensureUserRootCollectionExists(RoutingContext ctx, UserMapper userMapper, Handler<AsyncResult<Void>> handler) {
        Path resolvedPath = storage.getRoot().resolve(Paths.get(userMapper.getUuid()));

        storage.get(resolvedPath, resource -> {
            if (resource instanceof ErrorResource) {
                ErrorResource errorResource = (ErrorResource) resource;
                if (errorResource.isNotFound()) {
                    storage.putCollectionResource(resolvedPath, newResource -> {
                        logger.info("Created root directory for user {}", userMapper.getEmail());
                        handler.handle(Future.succeededFuture());
                    });
                }
            } else if (resource instanceof CollectionResource) {
                logger.info("Root directory for user {} exists", userMapper.getEmail());
                handler.handle(Future.succeededFuture());
            } else {
                handler.handle(Future.failedFuture(new BaseUserRequestException("Could not stat user root directory")));
            }
        });
    }
}
