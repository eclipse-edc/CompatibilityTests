# Compatibility tests for the EDC

This project contains compatibility tests for the Eclipse Dataspace Connector (EDC).

Currently, it tests the last stable EDC release against the `main` branch of the EDC.

## Quick start

To run the tests locally, we have to build the docker images for the stable version of the control plane and data plane.
with the following command:

```shell
./gradlew dockerize
````

Then, run the following command the compatibility tests:

```shell
./gradlew test -DincludeTags="EndToEndTest"
```

