package org.blackcat.trunk.streams.pump;

import java.util.Objects;

import io.vertx.core.spi.PumpFactory;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PumpFactoryImpl implements PumpFactory {
    @Override
    public <T> Pump pump(ReadStream<T> rs, WriteStream<T> ws) {
        Objects.requireNonNull(rs);
        Objects.requireNonNull(ws);
        return new PumpImpl<>(rs, ws);
    }

    @Override
    public <T> Pump pump(ReadStream<T> rs, WriteStream<T> ws, int writeQueueMaxSize) {
        Objects.requireNonNull(rs);
        Objects.requireNonNull(ws);
        return new PumpImpl<>(rs, ws, writeQueueMaxSize);
    }
}
