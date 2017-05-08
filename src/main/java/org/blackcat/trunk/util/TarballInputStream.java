package org.blackcat.trunk.util;

import org.blackcat.trunk.storage.Storage;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class TarballInputStream extends InputStream {

    private PipedInputStream in;
    private TarOutputStream out;

    private boolean canceled;

    public synchronized  boolean isCanceled() {
        return canceled;
    }

    public synchronized  void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    private static final Logger logger = LoggerFactory.getLogger(AsyncInputStream.class);

    public TarballInputStream(Storage storage, Path collectionPath) throws IOException {

        in = new PipedInputStream();
        out = new TarOutputStream(new PipedOutputStream(in));
        canceled = false;

        new Thread(() -> {

            List<Path> entries = storage.collectDirectory(storage.getRoot().resolve(collectionPath));
            logger.info("collected {} entries", entries.size());

            try {
                for (Path entry : entries) {
                    File file = entry.toFile();
                    String filename = storage.getRoot().relativize(entry).toString();

                    TarEntry tarEntry = new TarEntry(file, filename);
                    out.putNextEntry(tarEntry);

                    try (BufferedInputStream origin =
                                 new BufferedInputStream(new FileInputStream(file))) {

                        int count;
                        byte data[] = new byte[1024];

                        while (! isCanceled() && (count = origin.read(data)) != -1) {
                            out.write(data, 0, count);
                        }

                        /* graceful termination upon cancellation */
                        if (isCanceled())
                            break;

                        out.flush();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                /* closing the output stream */
                out.close();
                logger.info("tarball stream is now closed");
            } catch (IOException ioe) {
                logger.error(ioe.toString());
                throw new RuntimeException(ioe);
            }

        }).start();
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }
}