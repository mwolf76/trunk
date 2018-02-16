package org.blackcat.trunk.streams.async;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.blackcat.trunk.streams.async.impl.AsyncInputStreamImpl;

import java.io.InputStream;

/**
 * Wraps a regular InputStream into an AsyncInput Stream that can be used with
 * the Vert.X Pump mechanism
 */
abstract public class AsyncInputStream implements ReadStream<Buffer> {
    static public final AsyncInputStream create(Vertx vertx, InputStream in) {
        return new AsyncInputStreamImpl(vertx, in);
    }

    public abstract void close();
    public abstract void close(Handler<AsyncResult<Void>> handler);
}