package org.blackcat.trunk.http.requests.impl;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.middleware.UserInfoHandler;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;
import org.blackcat.trunk.util.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final public class GetSharingInformationHandlerImpl extends BaseUserRequestHandler implements UserInfoHandler {

    final private Logger logger = LoggerFactory.getLogger(GetSharingInformationHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);

        HttpServerRequest request = ctx.request();
        String requestPathString = Utils.urlDecode(request.path());
        Path requestPath = Paths.get(requestPathString);
        Path topLevelPath = Paths.get("/share");
        Path collectionPath = topLevelPath.relativize(requestPath);
        String collectionPathString = collectionPath.toString();

        String email = ctx.get("email");
        Queries.findCreateUserEntityByEmail(ctx.vertx(), email, (UserMapper userMapper) -> {

            Queries.findShareEntity(ctx.vertx(), collectionPath, shareMapper -> {
                JsonArray jsonArray = new JsonArray();

                List<String> authorizedUsers = shareMapper.getAuthorizedUsers();
                for (String authorizedUser : authorizedUsers) {
                    jsonArray.add(authorizedUser);
                }

                String body = new JsonObject()
                                  .put("data", new JsonObject()
                                                   .put("collectionPath", collectionPathString)
                                                   .put("authorizedUsers", jsonArray))
                                  .encodePrettily();

                ctx.response()
                    .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
                    .end(body);
            });
        });
    }
}
