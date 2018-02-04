package org.blackcat.trunk.http.requests.impl;

import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.ResponseBuilder;
import org.blackcat.trunk.http.requests.ProtectedIndexHandler;
import org.blackcat.trunk.queries.Queries;

public class ProtectedIndexHandlerImpl extends BaseUserRequestHandler implements ProtectedIndexHandler {
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
