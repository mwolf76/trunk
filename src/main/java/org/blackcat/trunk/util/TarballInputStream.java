package org.blackcat.trunk.util;

import io.vertx.core.Vertx;
import org.blackcat.trunk.storage.Storage;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TarballInputStream extends InputStream {

    private final Storage storage;
    private final PipedInputStream in;
    private final TarOutputStream out;
    private long totalBytesWritten = 0;

    private boolean canceled;

    public synchronized boolean isCanceled() {
        return canceled;
    }

    public synchronized void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    private static final Logger logger = LoggerFactory.getLogger(AsyncInputStream.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public TarballInputStream(Vertx vertx, Storage storage, Path collectionPath) throws IOException {
        this.in = new PipedInputStream();
        this.out = new TarOutputStream(new PipedOutputStream(in));
        this.storage = storage;

        executorService.submit(() -> {
            logger.info("Starting archiving task, using thread {}", Thread.currentThread());

            List<Path> entries;
            try (Stream<Path> pathStream = pathStream(storage, collectionPath)) {
                entries = pathStream
                              .filter(this::isRegularFile)
                              .collect(Collectors.toList());

                logger.debug("collected {} entries", entries.size());
                for (Path entry: entries) {
                    if (isCanceled())
                        break;

                    addEntry(storage, entry);
                    totalBytesWritten += appendEntryContents(entry);
                }
            } finally {
                closeOutputStream();
                logger.info("Archiving task terminated, releasing thread {}", Thread.currentThread());
            }
        });
    }

    private void closeOutputStream() {
        try {
            out.close();
            logger.info("Written {} bytes of content data. Tarball stream is now closed", totalBytesWritten);
        } catch (IOException ioe) {
            logger.error(ioe.toString());
            throw new RuntimeException(ioe);
        }
    }

    private void addEntry(Storage storage, Path entry) {
        logger.trace("adding {} ...", entry);
        String filename = relativeFilename(storage, entry);
        try {
            out.putNextEntry(new TarEntry(entry.toFile(), filename));
        } catch (IOException e) {
            logger.error("Could not add {} [{}]", filename, e.toString());
        }
    }

    private long appendEntryContents(Path path) {
        long bytesWritten = 0;
        try (BufferedInputStream origin = new BufferedInputStream(new FileInputStream(path.toFile()))) {

            int count;
            byte data[] = new byte[1024];

            while (!isCanceled() && (count = origin.read(data)) != -1) {
                bytesWritten += count;
                out.write(data, 0, count);
            }

            out.flush();
        } catch (IOException ioe) {
            logger.error(ioe.toString());
        }

        logger.trace("Wrote {} bytes", bytesWritten);
        return bytesWritten;
    }

    private String relativeFilename(Storage storage, Path entry) {
        return storage.getRoot().relativize(entry).toString();
    }

    private Stream<Path> pathStream(Storage storage, Path collectionPath) {
        return storage.streamDirectory(storage.getRoot().resolve(collectionPath));
    }

    private boolean isRegularFile(Path path) {
        return storage.resourceProperties(path).isRegularFile();
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }
}