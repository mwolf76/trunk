package org.blackcat.trunk.storage;

import io.vertx.core.Handler;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.resource.Resource;

import java.nio.file.Path;
import java.util.List;

public interface Storage {

    Path getRoot();

    /**
     * Verifies accessibility of user directory
     *
     * @param userMapper
     * @param done
     */
    void checkUserDirectory(UserMapper userMapper, Handler<Void> done);

    /**
     * Walks given directory, invoking action on each entry. Invokes done on completion.
     *
     * @param path
     * @param action
     * @param done
     */
    void walkDirectory(Path path, Handler<Path> action, Handler<Void> done);

    /**
     * Walks given directory, invoking action on each entry. Invokes done on completion.
     *
     * @param path
     */
    List<Path> collectDirectory(Path path);

    /**
     * Deletes a resource from the storage
     *
     * @param path the resource to be deleted from the storage
     * @param handler
     */
    void delete(Path path, Handler<Resource> handler);

    /**
     * Retrieves a resource from the storage
     *
     * @param path the resource to be retrieved from the storage
     * @param etag
     * @param handler
     */
    void get(Path path, String etag, Handler<Resource> handler);

    /**
     * Puts a collection resource(directory) on the storage
     *
     * @param path the resource to be put on the storage
     * @param etag
     * @param handler
     */
    void putCollection(Path path, String etag, Handler<Resource> handler);

    /**
     * Puts a document resource(file) on the storage
     *
     * @param path the resource to be put on the storage
     * @param etag
     * @param handler
     */
    void putDocument(Path path, String etag, Handler<Resource> handler);
}
