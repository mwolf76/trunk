package org.blackcat.trunk.queries;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.blackcat.trunk.eventbus.data.QueryType;
import org.blackcat.trunk.mappers.ShareMapper;
import org.blackcat.trunk.mappers.UserMapper;

import java.nio.file.Path;
import java.util.List;

public class Queries {
    /**
     * Retrieves a User entity by email, or creates a new one if no such entity exists.
     *
     * @param email
     * @param handler
     */
    static public void findCreateUserEntityByEmail(Vertx vertx, String email, Handler<UserMapper> handler) {
        JsonObject query = new JsonObject()
                               .put("type", QueryType.FIND_CREATE_USER.getTag())
                               .put("params", new JsonObject()
                                                  .put("email", email));

        vertx.eventBus()
            .send("data-store", query, reply -> {
                if (reply.failed())
                    handler.handle(null);
                else {
                    JsonObject obj = (JsonObject) reply.result().body();
                    handler.handle(obj.mapTo(UserMapper.class));
                }
            });
    }

    /**
     * Updates the sharing entity corresponding to collectionPath if such entity exists.
     * Creates a new one otherwise.
     *
     * @param owner
     * @param collectionPath
     * @param authorizedUsers
     * @param handler
     */
    static public void findOrUpdateShareEntity(Vertx vertx, UserMapper owner, Path collectionPath,
                                               List<String> authorizedUsers, Handler<ShareMapper> handler) {

        JsonObject query = new JsonObject()
                               .put("type", QueryType.FIND_UPDATE_SHARE.getTag())
                               .put("params", new JsonObject()
                                                  .put("owner", JsonObject.mapFrom(owner))
                                                  .put("collectionPath", collectionPath.toString())
                                                  .put("authorizedUsers", new JsonArray(authorizedUsers)));

        vertx.eventBus()
            .send("data-store", query, reply -> {
                if (reply.failed())
                    handler.handle(null);
                else {
                    JsonObject obj = (JsonObject) reply.result().body();
                    handler.handle(obj.mapTo(ShareMapper.class));
                }
            });
    }

    /**
     * Fetches an existing share entity related to collectionPath or NULL if no such entity exists.
     *
     * @param collectionPath
     * @param handler
     */
    static public void findShareEntity(Vertx vertx, Path collectionPath, Handler<ShareMapper> handler) {
        JsonObject query = new JsonObject()
                               .put("type", QueryType.FIND_SHARE.getTag())
                               .put("params", new JsonObject()
                                                  .put("collectionPath", collectionPath.toString()));
        vertx.eventBus()
            .send("data-store", query, reply -> {
                if (reply.failed())
                    handler.handle(null);
                else {
                    JsonObject obj = (JsonObject) reply.result().body();
                    handler.handle(obj.mapTo(ShareMapper.class));
                }
            });
    }
}
