package org.blackcat.trunk.storage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.file.FileProps;
import org.blackcat.trunk.resource.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface Storage {

    Path getRoot();

    /**
     * Stream interface to the file walker in the storage
     *
     * @param start
     * @return
     * @throws IOException
     */
    Stream<Path> streamDirectory(Path start) throws IOException;

    /**
     * Retrieves properties for path on the storage (blocking)
     *
     * @param path
     * @return
     * @throws IOException
     */
    FileProps resourceProperties(Path path) throws IOException;

    /**
     * Deletes a resource from the storage
     *  @param path the resource to be deleted from the storage
     * @param handler
     */
    void delete(Path path, Handler<AsyncResult<Void>> handler);

    /**
     * Retrieves a resource from the storage
     * @param path the resource to be retrieved from the storage
     * @param handler
     */
    void get(Path path, Handler<AsyncResult<Resource>> handler);

    /**
     * Puts a collection resource (i.e. directory) on the storage
     * @param path the resource to be put on the storage
     * @param handler
     */
    void putCollectionResource(Path path, Handler<AsyncResult<Void>> handler);

    /**
     * Puts a document resource (i.e. file) on the storage
     * @param path the resource to be put on the storage
     * @param handler
     */
    void putDocumentResource(Path path, Handler<AsyncResult<Resource>> handler);
}
