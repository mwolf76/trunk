package org.blackcat.trunk.http.requests.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.middleware.UserInfoHandler;

final public class LogoutHandlerImpl extends BaseUserRequestHandler implements UserInfoHandler {

    final private Logger logger = LoggerFactory.getLogger(GetResourceRequestHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        AccessToken user = (AccessToken) ctx.user();
        if (user == null) {
            cleanupAndRedirect(ctx);
        } else {
            user.logout(ar -> {
                if (ar.failed())
                    ctx.fail(ar.cause());
                else {
                    cleanupAndRedirect(ctx);
                }
            });
        }
    }

    private void cleanupAndRedirect(RoutingContext ctx) {
        String email = ctx.get("email");

        ctx.session().destroy();
        logger.info("Logged out user {}", email);
        responseBuilder.found(ctx, "/");
    }
}
