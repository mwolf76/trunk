# Trunk - your personal data repository

I started this project in 2017 to have something to experiment with
while learning to develop with the Vert.X toolkit
(http://vertx.io/). Trunk is a web application intended to act as an
online personal data storage.  Users can upload documents, organize
them into collections and share them with others users, granting
read-only access on a per-collection basis to other users. Under no
circumstance are the guests allowed to alter the contents of somebody
else's trunk.

## Build

To build the application, clone the repository and issue this command:

$ mvn clean package -DskipTests

Integration tests require some additional setup that involve setting a
local OAuth2 configuration with Keycloak. AFAICT, the quickest way to
to this boils down to:

* Bring up a docker container with pre-installed keycloak. Notice that
the keycloak server is exposed on port 9000.

```$ docker run --rm -p 9000:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin jboss/keycloak```

I suggest you run this in a separate terminal or even better in a tmux
instance and leave it running in the background. Refer to this video
for a step-by-step tutorial showing how to setup OAuth2 in Vert.X
using keycloak:

https://www.youtube.com/watch?v=c20igjL69Mo&index=1&list=PLK5alv8kedh0ul9NeuDgYYpX0I6yzjWw_

* Configure a keycloak client on the master realm using these settings:

  1. Base URL http://localhost:8080
  1. Confidential client

* Gather the following information from the Keycloak admin console:

  1. The master realm RSA public key
  1. The client ID (you pick a name)
  1. The client secret

* Write the configuration json file under conf/config.json. Here's a
  working example:

```
{
  "web": {
    "keystoreFilename": "server-keystore.jks",
    "keystorePassword": "wibble"
  },
  "database": {
    "type": "mongodb",
    "host": "localhost",
    "port": 27017,
    "name": "trunk"
  },
  "oauth2": {
    "provider": "keycloak",
    "clientID": "<YOUR-CLIENT-ID>"
    "clientSecret": "<YOUR-CLIENT-SECRET>",
    "keycloak": {
      "realmPublicKey": "<YOUR-REALM-RSA-PUBLIC-KEY>"
    }
  },
  "storage": {
    "root": "/tmp/trunk",
  }
}
```

* Create the db in mongo using mongo CLI tool (TODO: expand this)

* You should now be able to launch the application by deploying the
  main verticle:

  A script called `launch.sh` is included.

  FILE: launch.sh
  ```
  #!/bin/sh
  java -jar -Dvertx.logger-delegate-factory-class-name="io.vertx.core.logging.SLF4JLogDelegateFactory" \
  target/trunk-1.0-SNAPSHOT-fat.jar -conf conf/config.json run org.blackcat.trunk.verticles.TrunkVerticle | tee -a trunk.log
  ```

  You can launch the application just by issuing the command:
  ```$ ./launch.sh```

* You should now also be able to run the tests, by providing the three pieces of information
  mentioned above in the command line. A script called run-tests.sh is provided to help
  with launching the tests.

  FILE: run-tests.sh

    ```
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
  ```

  To launch the test issue the command: ```$ ./run-tests.sh```

## Application Usage

Point your browser to the base URL of the application. Clicking on the
"Open up!" link will redirect your browser to the OAuth2 provider
authentication service (currently public installs will use Google
Accounts, for local development and testing, Keycloak is also
supported).

Users in trunk are identified by the email. Upon successful
authentication your browser will be redirected to the user private
trunk's root collection. The contents of a trunk can be either
accessed in `owner` or `guest` mode. You can tell in which mode you're
in by looking at the top-left icon.  A 'house' indicates ownership of
the collection, a 'world' indicates you're looking at someone else's
contents.

Every user only has `owner` access to her trunk by default. However
the `owner` of a trunk can grant read-only access to a list of other
users on a per-collection basis. A `guest` can thus access somebody
else's trunk contents if they know the URL *and* have explicit
permission granted by the `owner` of that collection. To share a
resource with somebody, you have to know their email. You have to
grant them access (see below) *and* give them the URL needed to reach
that collection.

### Browsing resources

The contents of the currently selected collection are shown with
distinct icons for nested collections (book) and documents (sheet).
The user can browse the contents of any collection by clicking on its
link. The pill button on the right shows the number of inner items in
the collection. Clicking on a document will trigger a download on your
local. More information about documents are available by clicking the
pill button on the right. If you are the owner, the information detail
pop-up also contains two more tabs that let you update the document
and delete the document altogether.

### Guest (read-only operations)

The guest of a trunk has read permission to all accessible
contents. One extra feature of browsing contents is the bulk-download
button (the small cloud + download icon). Clicking on it triggers the
download of an uncompressed tarball containing all the collection.

### Ownership (content-altering operations)

In addition to have full read access to all of her trunk's contents,
the owner of a trunk can alter the contents of her personal repository
as follows:

* Creating a new collection (i.e. a directory). To create a new
collection click on the '+' button. The 'New Resource' pop-up will
show.  Enter the name for the new collection and click on the 'Commit'
button. The action can be cancelled by clicking on the 'Cancel'
button.  Browse to the newly created collection by clicking on its
link.

* Deleting a collection. To delete a collection click on '-' yellow
button. You will be asked for confirmation before proceeding with the
deletion of the collection. Click on 'Delete' button the confirm the
deletion. Click on the 'Cancel' button otherwise. Notice that the
yellow '-' shows up only if the collection is empty. To avoid costly
mistakes it is not possible to perform recursive resources deletions.

* Creating a new document (i.e. a file). To create a new document
click on the '+' button, and then navigate to the 'Document' tab.
Click on the 'Browse' button to activate the OS file selector. Pick
the document you want to upload, then click on the 'Commit' button o
upload the document to the currently selected collection. The action
can be cancelled by clicking on the 'Cancel' button. The newly created
document is now accessible by clicking on its link.

* Updating a document. To update a newer version of an existing
document, click on the pill button on the right of the document link,
then click on the 'Update' tab, click 'Browse' to select a new file
and finally 'Commit' to overwrite the previous contents of the
document with new contents. Caution: the previous contents of the
document will be gone forever!

* Deleting a document. To delete a document, click on the pill button
on the right of the document link, then click on the 'Delete' tab and
then confirm the deletion by clicking the 'Yes, delete this resource'
button. Caution: the contents of the document will be gone forever!

### Granting read-only access to your contents

As mentioned above, the user of a trunk can grant read-only
permissions to one or more users on a per-collection basis as follows:

* Enable collection sharing. Click on the 'Share' button. The 'Sharing
resource' pop-up shows up. You can now modify the (initially empty)
semicolon-separated list of users to whom you wish to grant read-only
access. Notice that you can also use the '*' wildcard to allow
read-only access to `anyone`. When you're done entering the new
sharing settings, click on the 'Commit' button. To leave unchanged
sharing settings for this collection, just click on the 'Close'
button.
