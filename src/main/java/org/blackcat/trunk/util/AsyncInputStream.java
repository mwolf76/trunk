package org.blackcat.trunk.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;

/**
 * Wraps a regular InputStream into an AsyncInput Stream that can be used with
 * the Vert.X Pump mechanism
 *
 * @author stw
 *
 */
public class AsyncInputStream implements ReadStream<Buffer> {

    // TODO: fix this to be in line with best performance
    public static final int           DEFAULT_READ_BUFFER_SIZE = 8192;
    private static final Logger       log                      = LoggerFactory.getLogger(AsyncInputStream.class);

    // Based on the inputStream with the real data
    private final ReadableByteChannel ch;
    private final Vertx               vertx;
    private final Context             context;

    private boolean                   closed;
    private boolean                   paused;
    private boolean                   readInProgress;

    private Handler<Buffer>           dataHandler;
    private Handler<Void>             endHandler;
    private Handler<Throwable>        exceptionHandler;

    /**
     * Create a new Async InputStream that can we used with a Pump
     *
     * @param in
     */
    public AsyncInputStream(final Vertx vertx, final InputStream in) {
        this.vertx = vertx;
        this.context = vertx.getOrCreateContext();
        this.ch = Channels.newChannel(in);
    }

    public void close() {
        this.closeInternal(null);
    }

    public void close(final Handler<AsyncResult<Void>> handler) {
        this.closeInternal(handler);
    }

    /*
     * (non-Javadoc)
     * @see io.vertx.core.streams.ReadStream#endHandler(io.vertx.core.Handler)
     */
    @Override
    public synchronized AsyncInputStream endHandler(final Handler<Void> endHandler) {
        this.check();
        this.endHandler = endHandler;
        return this;
    }

    /*
     * (non-Javadoc)
     * @see
     * io.vertx.core.streams.ReadStream#exceptionHandler(io.vertx.core.Handler)
     */
    @Override
    public synchronized AsyncInputStream exceptionHandler(final Handler<Throwable> exceptionHandler) {
        this.check();
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /*
     * (non-Javadoc)
     * @see io.vertx.core.streams.ReadStream#handler(io.vertx.core.Handler)
     */
    @Override
    public synchronized AsyncInputStream handler(final Handler<Buffer> handler) {
        this.check();
        this.dataHandler = handler;
        if ((this.dataHandler != null) && !this.paused && !this.closed) {
            this.doRead();
        }
        return this;
    }

    /*
     * (non-Javadoc)
     * @see io.vertx.core.streams.ReadStream#pause()
     */
    @Override
    public synchronized AsyncInputStream pause() {
        this.check();
        this.paused = true;
        return this;
    }

    /*
     * (non-Javadoc)
     * @see io.vertx.core.streams.ReadStream#resume()
     */
    @Override
    public synchronized AsyncInputStream resume() {
        this.check();
        if (this.paused && !this.closed) {
            this.paused = false;
            if (this.dataHandler != null) {
                this.doRead();
            }
        }
        return this;
    }

    private void check() {
        if (this.closed) {
            throw new IllegalStateException("Inputstream is closed");
        }
    }

    private void checkContext() {
        if (!this.vertx.getOrCreateContext().equals(this.context)) {
            throw new IllegalStateException("AsyncInputStream must only be used in the context that created it, expected: " + this.context
                    + " actual " + this.vertx.getOrCreateContext());
        }
    }

    private synchronized void closeInternal(final Handler<AsyncResult<Void>> handler) {
        this.check();
        this.closed = true;
        this.doClose(handler);
    }

    private void doClose(final Handler<AsyncResult<Void>> handler) {
        final Future<Void> res = Future.future();
        try {
            this.ch.close();
            res.complete(null);
        } catch (final IOException e) {
            res.fail(e);
        }
        if (handler != null) {
            this.vertx.runOnContext(v -> handler.handle(res));
        }
    }

    private synchronized void doRead() {
        // ReadableByteChannel doesn't have a completion handler, so we wrap it into
        // an executeBlocking and use the future there
        if (!this.readInProgress) {
            this.readInProgress = true;
            final ByteBuffer buff = ByteBuffer.allocate(AsyncInputStream.DEFAULT_READ_BUFFER_SIZE);

            this.vertx.executeBlocking(future -> {
                try {
                    final Integer bytesRead = this.ch.read(buff);
                    future.complete(bytesRead);
                } catch (final Exception e) {
                    AsyncInputStream.log.error(e);
                    future.fail(e);
                }
            } , res -> {
                if (res.failed()) {
                    this.context.runOnContext((v) -> this.handleException(res.cause()));
                } else {
                    // Buffer might be done
                    final Integer bytesRead = (Integer) res.result();
                    if (bytesRead < 0) {
                        // We are done, no more data to be expected
                        this.handleEnd();
                    } else {
                        buff.flip();
                        final Buffer vBuffer = Buffer.buffer(buff.limit());
                        vBuffer.setBytes(0, buff);
                        this.handleData(vBuffer);
                        this.context.runOnContext(v -> {
                            this.doRead();
                        });
                    }
                }
            });
        } else {
            // Reschedule the read
            if (!paused && !closed) {
                this.context.runOnContext(v -> {
                    this.doRead();
                });
            }
        }
    }

    private synchronized void handleData(final Buffer buffer) {
        if (this.dataHandler != null) {
            this.checkContext();
            this.dataHandler.handle(buffer);
        }
        // Processing complete
        this.readInProgress = false;
    }

    private synchronized void handleEnd() {
        this.paused = true;
        if (this.endHandler != null) {
            this.checkContext();
            this.endHandler.handle(null);
        }
    }

    private void handleException(final Throwable t) {
        if ((this.exceptionHandler != null) && (t instanceof Exception)) {
            this.exceptionHandler.handle(t);
        } else {
            AsyncInputStream.log.error("Unhandled exception", t);

        }
    }
}