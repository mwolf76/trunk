package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.ProtectedIndexHandler;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;

final public class ProtectedIndexHandlerImpl extends BaseUserRequestHandler implements ProtectedIndexHandler {

    final private Logger logger = LoggerFactory.getLogger(ProtectedIndexHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapperAsyncResult -> {
            if (userMapperAsyncResult.failed())
                ctx.fail(userMapperAsyncResult.cause());
            else {
                /* redirect to user contents */
                UserMapper userMapper = userMapperAsyncResult.result();
                String targetURI = userMapper.getUuid() + "/";
                responseBuilder.found(ctx, targetURI);
            }
        });
    }
}
