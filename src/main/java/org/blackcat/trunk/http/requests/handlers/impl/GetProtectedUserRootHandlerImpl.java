package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.GetProtectedUserRootHandler;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;

public class GetProtectedUserRootHandlerImpl extends BaseUserRequestHandler implements GetProtectedUserRootHandler {

    final private Logger logger = LoggerFactory.getLogger(ProtectedIndexHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        checkJsonRequest(ctx, ok -> {
            Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapperAsyncResult -> {
                if (userMapperAsyncResult.failed())
                    ctx.fail(userMapperAsyncResult.cause());
                else {
                    UserMapper userMapper = userMapperAsyncResult.result();
                    JsonObject res = new JsonObject()
                                         .put("email", userMapper.getEmail())
                                         .put("root", userMapper.getUuid());
                    jsonResponseBuilder.success(ctx, res);
                }
            });
        });
    }
}
