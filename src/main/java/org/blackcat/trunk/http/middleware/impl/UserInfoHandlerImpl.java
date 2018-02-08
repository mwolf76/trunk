package org.blackcat.trunk.http.middleware.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.middleware.UserInfoHandler;

final public class UserInfoHandlerImpl implements UserInfoHandler {

    final private Logger logger = LoggerFactory.getLogger(UserInfoHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        User user = ctx.user();
        if (user instanceof AccessToken) {
            AccessToken accessToken = (AccessToken) user;
            if (accessToken.expired()) {
                accessToken.refresh(ar -> {
                    if (ar.failed()) {
                        ctx.session().destroy();
                        ctx.fail(ar.cause());
                    }
                    else {
                        logger.info("Access Token refreshed!");
                        introspectAccessToken(ctx, accessToken);
                    }
                });
            } else {
                introspectAccessToken(ctx, accessToken);
            }
        }
    }

    private void introspectAccessToken(RoutingContext ctx, AccessToken accessToken) {
        accessToken.introspect(ar -> {
            if (ar.failed()) {
                ctx.session().destroy();
                ctx.fail(ar.cause());
            } else {
                JsonObject principal = accessToken.principal();
                String email = principal.getString("email");
                logger.info("User data: {}", principal.toString());

                ctx.put("email", email);
                ctx.next();
            }
        });
    }
}