package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.requests.handlers.ProtectedIndexHandler;
import org.blackcat.trunk.queries.Queries;

final public class ProtectedIndexHandlerImpl extends BaseUserRequestHandler implements ProtectedIndexHandler {

    final private Logger logger = LoggerFactory.getLogger(ProtectedIndexHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        Queries.findCreateUserEntityByEmail(ctx.vertx(), ctx.get("email"), userMapper -> {
            if (userMapper == null) {
                ctx.fail(new RuntimeException("Unexpected null UserMapper"));
            } else {
                /* redirect to user contents */
                String targetURI = userMapper.getUuid() + "/";
                responseBuilder.found(ctx, targetURI);
            }
        });

    }
}
