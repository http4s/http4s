---
menu: main
weight: 999 
title: Deployment 
---

## Overview

You've built and tested your service. How can we deploy it into production? One approach is to create an assembled `JAR` containing the service with all its dependencies. We can then execute it via `java -jar`. Another approach would be to create a native image binary via GRAAL. We will give each of these as examples below. 


### Assembled JAR

As an example of building an assembled JAR we can use `SBT` with the `sbt-assembly` plugin. Simliar approaches should exist for other build tools. Consult their documentation. For `SBT` we find the plugin https://github.com/sbt/sbt-assembly.

For a simple project we should be able to add the sbt-assembly plugin to `project/plugins.sbt` and then run the assembly task. We might need to use a merge strategy if some of our dependices have conflicting artifacts. Below is a simple example but following the documentation will give more specific configuration examples.


in project/plugins.sbt:

```
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "sbt-assembly-version")
```

Then as long as we have a `main` method in our project:

```
sbt assembly
```

should build our "fat" assembly jar. We should see the path of the assembly from the sbt log. For example `project/target/scala-2.13/project-assembly-0.0.1-SNAPSHOT.jar`

Finally we can execute the jar with 

```
java -jar /path.to/project-assembly-0.0.1-SNAPSHOT.jar
```

At this point you should see `http4s` start up. Regarding the artifact it might be a good idea to place it inside a `docker container` or create a service script via a `systemd unit` or similar.


### GRAAL NATIVE IMAGE

As an example of building a native image we will provide a reference for building a `static` native-image. Some explanation for building `static` is given at the end. But in short a `static` native-image should contain all the native libarary dependencies required to make a portable native binary.

Why would we create such an image? Some advantages might be faster startup times or less memory usage than the JVM.

#### Install GRAALVM and NATIVE IMAGE PLUGIN

The first step is to install the core `GraalVM` and `native-image` plugin. The core `GraalVM` release might be thought of as a replacement for the JVM. The `native-image` plugin is required to create the binary. You should be able to get the community edition builds from https://github.com/graalvm/graalvm-ce-builds/releases. 

After installing the core `GraalVM` you should be able to use it for as a `JDK`. For example you might set `JAVA_HOME` to point to your graal version. Otherwise your build platform might allow you to select the java library to build against graal. 

`JAVA_HOME` set 

```
export JAVA_HOME=/path/to/graalVM
```

then

```
java --version
``` 

should return something like

```
openjdk 11.0.6 2020-01-14
OpenJDK Runtime Environment GraalVM CE 20.0.0 (build 11.0.6+9-jvmci-20.0-b02)
OpenJDK 64-Bit Server VM GraalVM CE 20.0.0 (build 11.0.6+9-jvmci-20.0-b02, mixed mode, sharing)
```

#### Build an assembled jar using GRAALVM

After installing GRAALVM you should be able to build an assembled JAR. We can again use `SBT assembly` or your favorite build tool / plugin to create the assembled jar. The important thing is that we should be using the GRAALVM version of JAVA to do so.

#### Create the native image with the assembled JAR

After we have built the assembled jar containing all our java dependencies, we use that jar to build our native image. We can do so with the following command:

TODO: add instruction to obtain muslC
TODO: provide META-INF resources required for building image

```
native-image --initialize-at-build-time --static -H:UseMuslC="$PWD/muslC/bundle" --enable-http --enable-https --enable-all-security-services --verbose -jar ./target/scala-2.13/project-assembly-0.0.1-SNAPSHOT.jar projectBinaryImage
```

A breakout for the command parameters (image generation options) :

`-H:UseMuslC` to fix DNS and related segfaults and build a true static image that doesn't depend on linked libraries. 

`--initialize-at-build-time` again is related to building the static image. Again we lose performance if we instead do this at runtime.

`--enable-http`, `--enable-https` should correspond to related protocols

`--enable-all-security-services` will likely be required to make or receive https requests.

Additional image generation options are found in the native image reference: https://www.graalvm.org/docs/reference-manual/native-image/


#### Execute the native image

Finally we should be able to execute our output `projectBinaryImage`

```
./projectBinaryImage
```

At this point we may want to package the binary in a `docker` container, integrate the image generation to a greater build process, create `init` scripts via `systemd`, etc.

#### Why static? 

As an alternative to executing via the `JVM` `GraalVM native-image` allows us to execute as a native binary. For example in Linux environments this might be known as an `ELF` format. There are a number of ways to generate a native image be it `dynamic` or `static`. 

A `dynamic` image is less portable because it depends on shared libary files on each linux host. Then similar to creating an assembly jar containing all our java dependencies, when we build a `static` native image, we build
a more portable assembly including all the dependencies to run our binary across multiple platforms. For example we could expect a `static` ELF-64 binary to work across multiple linux distros of different versions for the same architecture. 


```tut:invisible
blockingPool.shutdown()
```

[service]: ../service
[entity]: ../entity
[json]: ../json
[`ContextShift`]: https://typelevel.org/cats-effect/datatypes/contextshift.html
[`ConcurrentEffect`]: https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html
[`IOApp`]: https://typelevel.org/cats-effect/datatypes/ioapp.html
[middleware]: ../middleware
[Follow Redirect]: ../api/org/http4s/client/middleware/FollowRedirect$
[Retry]: ../api/org/http4s/client/middleware/Retry$
[Metrics]: ../api/org/http4s/client/middleware/Metrics$
[Request Logger]: ../api/org/http4s/client/middleware/RequestLogger$
[Response Logger]: ../api/org/http4s/client/middleware/ResponseLogger$
[Logger]: ../api/org/http4s/client/middleware/Logger$
