package org.blackcat.trunk.streams.tar;

import org.blackcat.trunk.storage.Storage;
import org.blackcat.trunk.streams.tar.impl.TarballInputStreamImpl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public abstract class TarballInputStream extends InputStream {

    abstract public int read() throws IOException;

    abstract public boolean isCanceled();
    abstract public void setCanceled(boolean canceled);

    static public TarballInputStream create(Storage storage, Path collectionPath) {
        return new TarballInputStreamImpl(storage, collectionPath);
    }
    static public class TarballException extends RuntimeException {
        public TarballException(Throwable throwable) {
            super(throwable);
        }

    }
}