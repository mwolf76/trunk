#!/bin/sh
mvn clean package -DskipTests && \
    java -jar -Dvertx.logger-delegate-factory-class-name="io.vertx.core.logging.SLF4JLogDelegateFactory" target/trunk-1.0-SNAPSHOT-fat.jar -conf conf/config.json run org.blackcat.trunk.verticles.TrunkVerticle


