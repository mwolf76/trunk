package org.blackcat.trunk.http.session;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.web.RoutingContext;

final public class Session {
    private Session()
    {}

    public static String getSessionUserEmail(RoutingContext ctx) {
        User User = ctx.user();
        AccessToken at = (AccessToken) User;

        JsonObject idToken = KeycloakHelper.idToken(at.principal());
        String email = idToken.getString("email");

        return email;
    }
}
