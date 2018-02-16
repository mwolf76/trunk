package org.blackcat.trunk.storage.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.blackcat.trunk.resource.Resource;
import org.blackcat.trunk.resource.exceptions.ConflictException;
import org.blackcat.trunk.resource.exceptions.NotFoundException;
import org.blackcat.trunk.resource.exceptions.UnsupportedException;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.resource.impl.DocumentDescriptorResource;
import org.blackcat.trunk.storage.Storage;
import org.blackcat.trunk.storage.exceptions.StorageException;
import org.blackcat.trunk.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class FileSystemStorage implements Storage {

    private Logger logger = LoggerFactory.getLogger(FileSystemStorage.class);
    static private final OpenOptions openOptions = new OpenOptions();

    private Vertx vertx;
    private Path root;

    public FileSystemStorage(Vertx vertx, Path root) {
        this.vertx = vertx;
        this.root = root;
    }

    @Override
    public Path getRoot() {
        return root;
    }

    @Override
    public FileProps resourceProperties(Path path) {
        return vertx.fileSystem().propsBlocking(path.toString());
    }

    @Override
    public Stream<Path> streamDirectory(Path start) {
        try {
            return Files.walk(start);
        }
        catch (IOException ioe) {
            throw new StorageException(ioe);
        }
    }

    @Override
    public void get(Path path, Handler<AsyncResult<Resource>> resourceHandler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        /* if resource exists ... */
        String pathString = path.toString();
        fileSystem.exists(pathString, existsAsyncResult -> {
            if (existsAsyncResult.failed())
                resourceHandler.handle(Future.failedFuture(existsAsyncResult.cause()));

            else if (existsAsyncResult.result()) {
                fileSystem.props(pathString, filePropsAsyncResult -> {
                    normalResource(path, resourceHandler,
                        fileSystem, pathString, filePropsAsyncResult);
                });
            }

            /* Not found. Is it a `/meta` GET request? */
            else if (path.endsWith("meta")) {
                metaResource(path, resourceHandler, fileSystem);
            }

            else resourceHandler.handle(Future.failedFuture(new NotFoundException()));
        });
    } /* get() */

    private void normalResource(Path path, Handler<AsyncResult<Resource>> resourceHandler, FileSystem fileSystem,
                                String pathString, AsyncResult<FileProps> filePropsAsyncResult) {
        FileProps fileProperties = filePropsAsyncResult.result();

        /* Directory? */
        if (fileProperties.isDirectory()) {
            fileSystem.readDir(pathString, dirAsyncResult -> {

                vertx.executeBlocking(future -> {
                    CollectionResource collectionResource =
                        new CollectionResource();

                    for (String entry : dirAsyncResult.result()) {
                        Path entryPath = Paths.get(entry);
                        String entryPathString = entryPath.toString();

                        Path entryName = path.relativize(entryPath);
                        String entryNameString = entryName.toString();

                        /* ignore hidden files */
                        if (entryNameString.startsWith("."))
                            continue;

                        FileProps fileProps = fileSystem.propsBlocking(entry);
                        if (fileProps.isDirectory()) {
                            try {
                                List<String> nestedEntries = fileSystem.readDirBlocking(entryPathString);
                                collectionResource.addItem(
                                    new CollectionResource(
                                        entryNameString, nestedEntries.size()));
                            } catch (RuntimeException re) {
                                logger.warn("Skipping unreadable directory: {0}", entryPath);
                            }
                        } else if (fileProps.isRegularFile()) {
                            String mimeType = getMimeType(entryPath);
                            collectionResource.addItem(
                                new DocumentDescriptorResource(entryNameString, mimeType,
                                    fileProps.creationTime(), fileProps.lastModifiedTime(),
                                    fileProps.lastAccessTime(), fileProps.size()));
                        } else {
                            logger.warn("Unexpected filesystem object: {0}", entryName);
                        }
                    }

                    future.complete(collectionResource);
                }, done -> {
                    final CollectionResource collectionResource = (CollectionResource) done.result();
                    resourceHandler.handle(Future.succeededFuture(collectionResource));
                });
            });
        }

        /* Regular file? */
        else if (fileProperties.isRegularFile()) {
            fileSystem.open(pathString, openOptions, openAsyncResult -> {
                AsyncFile asyncFile = openAsyncResult.result();
                try {
                    String mimeType = Files.probeContentType(path);
                    DocumentContentResource documentContentResource =
                        new DocumentContentResource(mimeType, fileProperties.size(),
                            asyncFile, event -> {
                            logger.trace("Closing input stream");
                            asyncFile.close();
                        });

                    resourceHandler.handle(Future.succeededFuture(documentContentResource));
                } catch (IOException ioe) {
                    resourceHandler.handle(Future.failedFuture(new ConflictException(ioe)));
                }
            });
        }

        else {
            resourceHandler.handle(Future.failedFuture(new UnsupportedOperationException()));
        }
    }

    private void metaResource(Path path, Handler<AsyncResult<Resource>> resourceHandler, FileSystem fileSystem) {
        Path resourcePath = path.getParent();
        String resourcePathString = resourcePath.toString();

        Path resourceFileName = resourcePath.getFileName();
        String resourceFileNameString = resourceFileName.toString();

        /* if resource exists ... */
        fileSystem.exists(resourcePathString, actualExistsAsyncResult -> {
            Boolean actualExists = actualExistsAsyncResult.result();
            if (actualExists) {
                fileSystem.props(resourcePathString, filePropsAsyncResult -> {
                    FileProps fileProperties = filePropsAsyncResult.result();

                    if (fileProperties.isDirectory()) {
                        // TODO: 2/13/18 meta not yet supported for collections
                        resourceHandler.handle(Future.failedFuture(new UnsupportedException()));
                    } else if (fileProperties.isRegularFile()) {
                        String mimeType = getMimeType(resourcePath);
                        DocumentDescriptorResource documentDescriptorResource =
                            new DocumentDescriptorResource(resourceFileNameString, mimeType,
                                fileProperties.creationTime(), fileProperties.lastModifiedTime(),
                                fileProperties.lastAccessTime(), fileProperties.size());

                        resourceHandler.handle(Future.succeededFuture(documentDescriptorResource));
                    }
                });
            }
        });
    }

    @Override
    public void putCollectionResource(Path path, Handler<AsyncResult<Void>> resourceHandler) {
        FileSystem fileSystem = vertx.fileSystem();
        String pathString = path.toString();

        fileSystem.exists(pathString, existsAsyncResult -> {
            if (existsAsyncResult.failed())
                resourceHandler.handle(Future.failedFuture(existsAsyncResult.cause()));

            else if (existsAsyncResult.result()) {
                fileSystem.props(pathString, propsAsyncResult -> {
                    FileProps props = propsAsyncResult.result();

                    /* if resource already exists and it's a collection we're good */
                    if (props.isDirectory()) {
                        resourceHandler.handle(Future.succeededFuture());
                    }

                    /* if resource already exists and it's not a directory we complain */
                    else if (props.isRegularFile()) {
                        resourceHandler.handle(Future.failedFuture(
                            new ConflictException("A document with the same name already exists.")));
                    }
                });
            }

            /* Resource does not exist, we *require* to parent directory to exist to create it. */
            else {
                Path parentPath = path.getParent();
                String parentPathString = parentPath.toString();

                fileSystem.exists(parentPathString, parentExistsAsyncResult -> {
                    if (parentExistsAsyncResult.failed())
                        resourceHandler.handle(Future.failedFuture(parentExistsAsyncResult.cause()));

                    else if (parentExistsAsyncResult.result()) {
                        mkDir(resourceHandler, fileSystem, pathString);
                    } else {
                        logger.warn("Can not create collection resource {}. Parent does not exist.", pathString);
                        resourceHandler.handle(Future.failedFuture(
                            new ConflictException("Can not create collection resource.")));
                    }
                });
            }
        });
    } /* putCollectionResource() */

    private void mkDir(Handler<AsyncResult<Void>> resourceHandler, FileSystem fileSystem, String pathString) {
        fileSystem.mkdirs(pathString, asyncMkdirsResult -> {
            if (asyncMkdirsResult.failed())
                resourceHandler.handle(Future.failedFuture(
                    new ConflictException(asyncMkdirsResult.cause())));
            else {
                resourceHandler.handle(Future.succeededFuture());
            }
        });
    }

    @Override
    public void putDocumentResource(Path path, Handler<AsyncResult<Resource>> resourceHandler) {
        FileSystem fileSystem = vertx.fileSystem();
        String pathString = path.toString();

        fileSystem.exists(pathString, existsAsyncResult -> {
            if (existsAsyncResult.failed())
                resourceHandler.handle(Future.failedFuture(existsAsyncResult.cause()));

            else if (existsAsyncResult.result()) {
                fileSystem.props(pathString, propsAsyncResult -> {
                    FileProps props = propsAsyncResult.result();

                    /* if resource already exists and it's a collection we complain */
                    if (props.isDirectory()) {
                        resourceHandler.handle(Future.failedFuture(
                            new ConflictException("A collection with the same name already exists.")));
                    }

                    /* ... we overwrite it */
                    else if (props.isRegularFile()) {
                        putDocumentHelper(path, resourceHandler, completed -> {
                            resourceHandler.handle(Future.succeededFuture());
                        });
                    }

                    /* ??? */
                    else {
                        resourceHandler.handle(Future.failedFuture(new ConflictException(
                            "Existing resource is neither a collection nor a document.")));
                    }
                });
            }

            /* Resource does not exist, we require existence of the parent collection */
            else {
                Path parentPath = path.getParent();
                String parentPathString = parentPath.toString();

                fileSystem.exists(parentPathString, parentExistsAsyncResult -> {
                    if (parentExistsAsyncResult.failed())
                        resourceHandler.handle(Future.failedFuture(parentExistsAsyncResult.cause()));

                    else if (parentExistsAsyncResult.result()) {
                        putDocumentHelper(path, resourceHandler, completed -> {
                            resourceHandler.handle(Future.succeededFuture());
                        });
                    } else {
                        resourceHandler.handle(Future.failedFuture(new ConflictException(
                            "Parent collection not existing.")));
                    }
                });
            }
        });
    } /* putDocumentResource() */

    private void putDocumentHelper(Path fullPath,
                                   Handler<AsyncResult<Resource>> handler,
                                   Handler<Void> completionHandler) {

        FileSystem fileSystem = vertx.fileSystem();
        String destPathString = fullPath.toString();
        String tempPathString = Utils.makeTempFileName(destPathString);

        fileSystem.open(tempPathString, openOptions, openAsyncResult -> {
            if (openAsyncResult.failed())
                handler.handle(Future.failedFuture(new ConflictException(openAsyncResult.cause())));

            else {
                AsyncFile asyncFile =
                    openAsyncResult.result();

                // TODO: 2/13/18 this is way too hard to read. Refactor it!
                DocumentContentResource documentContentResource =
                    new DocumentContentResource(asyncFile,
                        done -> completeTransfer(fileSystem, asyncFile, destPathString, tempPathString, completionHandler));

                handler.handle(Future.succeededFuture(documentContentResource));
            }
        });
    } /* putDocumentHelper() */

    private void completeTransfer(FileSystem fileSystem, AsyncFile asyncFile, String destPathString,
                                  String tempPathString, Handler<Void> completionHandler) {
        asyncFile.close(
            closeAsyncResult -> {
                fileSystem.delete(destPathString,
                    deleteAsyncResult -> {
                        fileSystem.move(tempPathString, destPathString,
                            moveAsyncResult -> {
                                fileSystem.props(destPathString, filePropsAsyncResult -> {
                                    if (filePropsAsyncResult.succeeded()) {
                                        FileProps result = filePropsAsyncResult.result();
                                        logger.info("File transfer operation completed ({} bytes written).",
                                            result.size());
                                        completionHandler.handle(null);
                                    }
                                });
                            });
                    });
            });
    }

    @Nullable
    private String getMimeType(Path resourcePath) {
        String mimeType = null;
        try {
            mimeType = Files.probeContentType(resourcePath);
        } catch (IOException e) {
            logger.warn("Could not determine mime type for {}", resourcePath);
        }
        return mimeType;
    }

    @Override
    public void delete(Path path, Handler<AsyncResult<Void>> resourceHandler) {
        FileSystem fileSystem = vertx.fileSystem();

        String pathString = path.toString();
        fileSystem.exists(pathString, existsAsyncResult -> {
            if (existsAsyncResult.failed())
                resourceHandler.handle(Future.failedFuture(existsAsyncResult.cause()));
            else {
                if (! existsAsyncResult.result()) {
                    resourceHandler.handle(Future.failedFuture(new NotFoundException()));
                } else {
                    deleteResource(fileSystem, pathString, resourceHandler);
                }
            }
        });
    } /* delete() */

    private void deleteResource(FileSystem fileSystem, String pathString,
                                Handler<AsyncResult<Void>> resourceHandler) {
        fileSystem.delete(pathString, deleteAsyncResult -> {
            if (deleteAsyncResult.failed())
                resourceHandler.handle(Future.failedFuture(
                    new ConflictException(deleteAsyncResult.cause())));
            else
                resourceHandler.handle(Future.succeededFuture());
        });
    }

    public static FileSystemStorage create(Vertx vertx, Path path) {
        return new FileSystemStorage(vertx, path);
    }
}
