package org.blackcat.trunk.resource.impl;

import io.vertx.core.json.JsonObject;
import org.blackcat.trunk.util.Utils;

import java.time.Instant;
import java.time.ZoneOffset;

final public class DocumentDescriptorResource extends BaseResource {
    @Override
    public int getOrdering() {
        return 1;
    }

    final private String mimeType;
    final private long creationTime;
    final private long lastModificationTime;
    final private long lastAccessedTime;
    final private long length;

    public DocumentDescriptorResource(String name,
                                      String mimeType,
                                      long creationTime,
                                      long lastModificationTime,
                                      long lastAccessedTime,
                                      long length) {
        setName(name);
        this.mimeType = mimeType;
        this.creationTime = creationTime;
        this.lastModificationTime = lastModificationTime;
        this.lastAccessedTime = lastAccessedTime;
        this.length = length;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getHumanCreationTime() {
        return Instant.ofEpochMilli(creationTime)
                .atOffset(ZoneOffset.UTC).toString();
    }


    public long getLastModificationTime() {
        return lastModificationTime;
    }

    public String getHumanLastModificationTime() {
        return Instant.ofEpochMilli(lastModificationTime)
                .atOffset(ZoneOffset.UTC).toString();
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public String getHumanLastAccessedTime() {
        return Instant.ofEpochMilli(lastAccessedTime)
                .atOffset(ZoneOffset.UTC).toString();
    }

    public long getLength() {
        return length;
    }

    public String getHumanLength() {
        return Utils.humanReadableByteCount(length);
    }

    public String getMetadata() {
        final JsonObject obj = new JsonObject()
                .put("name", getName())
                .put("mimeType", getMimeType())
                .put("created", getHumanCreationTime())
                .put("modified", getHumanLastModificationTime())
                .put("accessed", getHumanLastAccessedTime())
                .put("length", getHumanLength());

        return obj.toString();
    }
}
