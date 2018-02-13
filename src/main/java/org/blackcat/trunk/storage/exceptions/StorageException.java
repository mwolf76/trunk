package org.blackcat.trunk.storage.exceptions;

import org.blackcat.trunk.resource.exceptions.ResourceException;

public class StorageException extends ResourceException {
    public StorageException() {
    }

    public StorageException(String s) {
        super(s);
    }

    public StorageException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public StorageException(Throwable throwable) {
        super(throwable);
    }

    public StorageException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
