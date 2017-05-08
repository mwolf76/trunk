package org.blackcat.trunk.storage.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.*;
import io.vertx.core.logging.Logger;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.resource.Resource;
import org.blackcat.trunk.resource.impl.CollectionResource;
import org.blackcat.trunk.resource.impl.DocumentContentResource;
import org.blackcat.trunk.resource.impl.DocumentDescriptorResource;
import org.blackcat.trunk.resource.impl.ErrorResource;
import org.blackcat.trunk.storage.Storage;
import org.blackcat.trunk.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.blackcat.trunk.util.Utils.urlEncode;

public class FileSystemStorage implements Storage {

    private Vertx vertx;
    private Logger logger;
    private Path root;

    static private final OpenOptions openOptions =
            new OpenOptions();

    public FileSystemStorage(Vertx vertx, Logger logger, Path root) {
        this.vertx = vertx;
        this.logger = logger;
        this.root = root;
    }

    @Override
    public Path getRoot() {
        return root;
    }

    @Override
    public void checkUserDirectory(UserMapper userMapper, Handler<Void> handler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        Path userDirectoryPath = root.resolve(Paths.get(userMapper.getUuid()));
        String userDirectoryPathString = userDirectoryPath.toString();
        fileSystem.exists(userDirectoryPathString, existsAsyncResult -> {
            Boolean exists = existsAsyncResult.result();
            if (exists) {
                fileSystem.props(userDirectoryPathString, filePropsAsyncResult -> {
                    FileProps fileProperties = filePropsAsyncResult.result();

                    /* Directory? */
                    if (fileProperties.isDirectory()) {
                        handler.handle(null); /* all good */
                    } else {
                        throw new FileSystemException(MessageFormat.format(
                                "Path {0} already exists and it's not a directory.",
                                userDirectoryPath
                        ));
                    }
                });
            } else {
                fileSystem.mkdir(userDirectoryPathString, mkdirDirAsyncResult -> {
                    if (mkdirDirAsyncResult.succeeded())
                        handler.handle(null); /* all good */
                    else {
                        throw new FileSystemException(MessageFormat.format(
                                "Could not make directory {0} for user {1}.",
                                userDirectoryPath, userMapper.getEmail()));
                    }
                });
            }
        });
    }

    @Override
    public List<Path> collectDirectory(Path start) {

        List<Path> res = null;

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        try (Stream<Path> walk = Files.walk(start)) {
            res = walk
                    .filter(new Predicate<Path>() {
                        @Override
                        public boolean test(Path path) {
                            final FileProps fileProps = fileSystem.propsBlocking(path.toString());
                            return fileProps.isRegularFile();
                        }
                    })
                    .collect(Collectors.toList());
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        return res;
    }

    @Override
    public void walkDirectory(Path start, Handler<Path> action, Handler<Void> done) {
        try (Stream<Path> walk = Files.walk(start)) {
            walk.forEach(new Consumer<Path>() {
                @Override
                public void accept(Path path) {
                        action.handle(path);
                }
            });
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Override
    public void get(Path path, String etag, Handler<Resource> resourceHandler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        /* if resource exists ... */
        String pathString = path.toString();
        fileSystem.exists(pathString, existsAsyncResult -> {
            Boolean exists = existsAsyncResult.result();

            if (exists) {
                fileSystem.props(pathString, filePropsAsyncResult -> {
                    FileProps fileProperties = filePropsAsyncResult.result();

                    /* Directory? */
                    if (fileProperties.isDirectory()) {
                        fileSystem.readDir(pathString, dirAsyncResult -> {

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
                                        logger.warn( "Skipping unreadable directory: {0}", entryPath);
                                    }
                                } else if (fileProps.isRegularFile()) {
                                    String resourceURL = urlEncode(entryNameString);
                                    String mimeType = null;
                                    try {
                                        mimeType = Files.probeContentType(entryPath);
                                    } catch (IOException e) {
                                        logger.warn("Could not determine mime type for {}", entryPath);
                                    }
                                    collectionResource.addItem(
                                            new DocumentDescriptorResource(entryNameString, mimeType,
                                                    fileProps.creationTime(), fileProps.lastModifiedTime(),
                                                    fileProps.lastAccessTime(), fileProps.size()));
                                } else {
                                    logger.warn("Unexpected filesystem object: {0}", entryName);
                                }
                            }

                            resourceHandler.handle(collectionResource);
                        });
                    }

                    /* Regular file? */
                    else if (fileProperties.isRegularFile()) {
                        fileSystem.open(pathString, openOptions, openAsyncResult -> {
                            AsyncFile asyncFile = openAsyncResult.result();

                            try {
                                String mimeType = Files.probeContentType(path);
                                DocumentContentResource documentContentResource =
                                        new DocumentContentResource(mimeType, fileProperties.size(), asyncFile, event -> {
                                            logger.trace("Closing input stream");
                                            asyncFile.close();
                                        });

                                resourceHandler.handle(documentContentResource);
                            }
                            catch (IOException ioe) {
                                resourceHandler.handle(ErrorResource.makeInvalid(ioe.toString()));
                            }
                        });
                    }

                    /* Invalid resource */
                    else {
                        resourceHandler.handle(ErrorResource.makeInvalid("Unsupported resource type"));
                    }
                });
            }

            /* Not found. Is it a `/meta` GET request? */
            else if (path.endsWith("meta")) {
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
                                /* TODO: meta not yet supported for collections */
                                resourceHandler.handle(ErrorResource.makeRejected());
                            }
                            else if (fileProperties.isRegularFile()) {
                                String mimeType = null;
                                try {
                                    mimeType = Files.probeContentType(resourcePath);
                                } catch (IOException e) {
                                    logger.warn("Could not determine mime type for {}", resourcePath);
                                }
                                DocumentDescriptorResource documentDescriptorResource =
                                        new DocumentDescriptorResource(resourceFileNameString, mimeType,
                                                fileProperties.creationTime(), fileProperties.lastModifiedTime(),
                                                fileProperties.lastAccessTime(), fileProperties.size());

                                resourceHandler.handle(documentDescriptorResource);
                            }
                        });
                    }
                });
            }

            else {
                resourceHandler.handle(ErrorResource.makeNotFound());
            }
        });
    } /* get() */

    @Override
    public void putCollection(Path path, String etag, Handler<Resource> resourceHandler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        String pathString = path.toString();

        fileSystem.exists(pathString, existsAsyncResult -> {
            Boolean exists = existsAsyncResult.result();

            if (exists) {
                fileSystem.props(pathString, propsAsyncResult -> {
                    FileProps props = propsAsyncResult.result();

                    /* if resource already exists and it's a collection we're good */
                    if (props.isDirectory()) {
                        Resource res = ErrorResource.makeUnit();
                        resourceHandler.handle(res);
                        return;
                    }

                    /* if resource already exists and it's not a directory we complain */
                    else if (props.isRegularFile()) {
                        Resource res = ErrorResource.makeInvalid("A document with the same name already exists.");
                        resourceHandler.handle(res);
                        return;
                    }
                });
            }

            /* Resource does not exist, we *require* to parent directory to exist */
            else {
                Path parentPath = path.getParent();
                String parentPathString = parentPath.toString();
                fileSystem.exists(parentPathString, parentExistsAsyncResult -> {
                    Boolean parentExists = parentExistsAsyncResult.result();
                    if (parentExists) {
                        fileSystem.mkdirs(pathString, asyncMkdirsResult -> {
                            Resource res = (asyncMkdirsResult.succeeded())
                                    ? ErrorResource.makeUnit()
                                    : ErrorResource.makeInvalid(asyncMkdirsResult.cause().toString())
                                    ;
                            resourceHandler.handle(res);
                        });
                    }
                 });
            }
        });
    } /* putCollection() */

    @Override
    public void putDocument(Path path, String etag, Handler<Resource> resourceHandler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        String pathString = path.toString();
        fileSystem.exists(pathString, existsAsyncResult -> {
            Boolean exists = existsAsyncResult.result();

            if (exists) {
                fileSystem.props(pathString, propsAsyncResult -> {
                    FileProps props = propsAsyncResult.result();

                    /* if resource already exists and it's a collection we complain */
                    if (props.isDirectory()) {
                        Resource res = ErrorResource.makeInvalid("A collection with the same name already exists.");
                        resourceHandler.handle(res);
                    }

                    /* ... we overwrite it */
                    else if (props.isRegularFile()) {
                        putDocumentHelper(path, resourceHandler, event -> {
                            resourceHandler.handle(ErrorResource.makeUnit());
                        });
                        return;
                    }

                    /* ??? */
                    else {
                        resourceHandler.handle( ErrorResource.makeInvalid(
                                "Existing resource is neither a collection nor a document."));
                    }
                });
            }

            /* Resource does not exist, we require existence of the parent collection */
            else {
                Path parentPath = path.getParent();
                String parentPathString = parentPath.toString();

                fileSystem.exists(parentPathString, parentExistsAsyncResult -> {

                    Boolean parentExists = parentExistsAsyncResult.result();
                    if (parentExists) {
                        putDocumentHelper(path, resourceHandler, event -> {
                            resourceHandler.handle(ErrorResource.makeUnit());
                        });
                    } else {
                        resourceHandler.handle( ErrorResource.makeInvalid(
                                "Parent collection not existing."));
                    }
                });
            }
        });
    } /* putDocument() */

    private void putDocumentHelper(final Path fullPath, final Handler<Resource> handler,
                                   final Handler<Void> completionHandler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        String destPathString = fullPath.toString();
        String tempPathString = Utils.makeTempFileName(destPathString);

        fileSystem.open(tempPathString, openOptions, openAsyncResult -> {
            if (openAsyncResult.succeeded()) {

                final AsyncFile asyncFile =
                        openAsyncResult.result();

                final DocumentContentResource documentContentResource =
                        new DocumentContentResource(asyncFile, dummy ->
                                asyncFile.close(closeAsyncResult ->
                                        fileSystem.delete(destPathString, deleteAsyncResult ->
                                                fileSystem.move(tempPathString, destPathString, moveAsyncResult -> {
                                                    logger.info("Operation completed.");
                                                    completionHandler.handle(null);
                                                }))));

                handler.handle(documentContentResource);
            }
            else {
                handler.handle(ErrorResource.makeInvalid(openAsyncResult.cause().toString()));
            }
        });
    } /* putDocumentHelper() */

    @Override
    public void delete(Path path, Handler<Resource> resourceHandler) {

        /* local ref to the filesystem object */
        FileSystem fileSystem = vertx.fileSystem();

        String pathString = path.toString();

        fileSystem.exists(pathString, existsAsyncResult -> {
            final Boolean exists = existsAsyncResult.result();

            if (! exists) {
                resourceHandler.handle(ErrorResource.makeNotFound());
            } else {
                fileSystem.delete(pathString, deleteAsyncResult -> {
                    Resource resource = deleteAsyncResult.succeeded()
                            ? ErrorResource.makeUnit()
                            : ErrorResource.makeInvalid(deleteAsyncResult.cause().toString());

                    resourceHandler.handle(resource);
                });
            }
        });
    } /* delete() */
}
