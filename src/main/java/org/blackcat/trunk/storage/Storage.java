package org.blackcat.trunk.storage;

import io.vertx.core.Handler;
import io.vertx.core.file.FileProps;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.resource.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

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
     * Stream interface to the file walker in the storage
     *
     * @param start
     * @return
     * @throws IOException
     */
    public Stream<Path> streamDirectory(Path start) throws IOException;

    /**
     * Retrieves properties for path on the storage (blocking)
     *
     * @param path
     * @return
     * @throws IOException
     */
    FileProps pathProperties(Path path) throws IOException;

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
