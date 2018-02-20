#!/bin/sh

# these parameters need to be changed according to the local Keycloak configuration before launching the tests
clientID="trunk"
clientSecret="9a716081-622e-48e9-9930-58beb765f4e6"
realmPublicKey="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2CUVHi4NyR9vNbTR6pDsHe9gc6Sp3cnMhrkZIzAg69h6QjVdtXe8aWoetidXbF9pUkrcsztKfVHOB1Rzdzm7IEMGw7u5PVcOyAc8MFgD379Hmx1tfmmlXS3q9IxpRFioFnRJtfr1noOlxHK4aMZBa0NqwZ6aYfwnaNX5F8uHM50kk1FPD3bAw2fefmp/UkrkucHjGYdQ3d65JHvs9DnUkp0VTGe2tVsNkVA5ZXKZrqqrfSfmbh//jYSWohkMjhGfjMT1bLIN4Kgi4D7P3g1dUvTiJi8pak1WL73VI1xvpsQebDgHvLBqqZXKuzp9Txf5/rmdfS0E51a6LevaEM9aZQIDAQAB"

mvn -DclientID="$clientID" \
    -DclientSecret="$clientSecret" \
    -DrealmPublicKey="$realmPublicKey" \
    -Dvertx.logger-delegate-factory-class-name="io.vertx.core.logging.SLF4JLogDelegateFactory" \
    test

