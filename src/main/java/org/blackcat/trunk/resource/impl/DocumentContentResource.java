package org.blackcat.trunk.resource.impl;

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

public final class DocumentContentResource extends BaseResource {

    @Override
    public int getOrdering() {
        return 1;
    }

    private long length;
    public long getLength() {
        return length;
    }

    private String etag;
    public String getEtag() {
        return etag;
    }

    private String mimetype;
    public String getMimeType() {
        return mimetype;
    }

    private ReadStream readStream;
    public ReadStream getReadStream() {
        return readStream;
    }

    private WriteStream writeStream;
    public WriteStream getWriteStream() {
        return writeStream;
    }

    private Handler<Void> closeHandler;
    public Handler<Void> getCloseHandler() {
        return closeHandler;
    }

    public DocumentContentResource(String mimetype, long length, ReadStream readStream, Handler<Void> closeHandler) {
        this.mimetype = mimetype;
        this.length = length;
        this.readStream = readStream;
        this.closeHandler = closeHandler;
    }

    public DocumentContentResource(WriteStream writeStream, Handler<Void> closeHandler) {
        this.writeStream = writeStream;
        this.closeHandler = closeHandler;
    }
}
