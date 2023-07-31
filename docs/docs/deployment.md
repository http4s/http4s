# Deployment

## Overview

You've built and tested your service. How can we deploy it into production? One approach is to create an assembled JAR containing the service with all its dependencies. We can then execute it via `java -jar`. Another approach would be to create a native image binary via [GraalVM](https://www.graalvm.org/). We will give each of these as examples below.


### Assembled JAR

As an example of building an assembled JAR we can use SBT with the `sbt-assembly` plugin. Simliar approaches should exist for other build tools. Consult their documentation. For SBT we find the plugin https://github.com/sbt/sbt-assembly.

For a simple project we should be able to add the sbt-assembly plugin to `project/plugins.sbt` and then run the `assembly` task. We might need to use a merge strategy if some of our dependencies have conflicting artifacts. Below is a simple example, but following the documentation will give more specific configuration examples.


in project/plugins.sbt:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "sbt-assembly-version")
```

Then as long as we have a `main` method in our project:

```sh
sbt assembly
```

should build our "fat" assembly jar. We should see the path of the assembly from the sbt log. For example `target/scala-2.13/project-assembly-0.0.1-SNAPSHOT.jar`

Finally we can execute the jar with

```sh
java -jar /path.to/project-assembly-0.0.1-SNAPSHOT.jar
```

At this point you should see your http4s server start up. Regarding the artifact, it might be a good idea to place it inside a docker container or create a service script via a systemd unit or similar.


### Graal Native Image

We provide an outline for building a static native image below. Furthermore the [http4s giter8 template](https://github.com/http4s/http4s.g8) can be used to build a native image in conjunction with this guide.

Why would we create such an image? Some advantages might be faster startup times or less memory usage than the JVM.

#### Install GraalVM and Native Image plugin

The first step is to install the core GraalVM and `native-image` plugin. The core GraalVM release might be thought of as a replacement for the JVM. The `native-image` plugin is required to create the binary. The [community edition builds](https://github.com/graalvm/graalvm-ce-builds/releases) are free.

After installing the core GraalVM you should be able to use it as a JDK. For example you might set `JAVA_HOME` to point to your Graal version. Otherwise, your build platform might allow you to select the Java library to build against Graal.

```sh
export JAVA_HOME=/path/to/graalVM
```

then

```sh
java --version
```

should return something like

```
openjdk 11.0.6 2020-01-14
OpenJDK Runtime Environment GraalVM CE 20.0.0 (build 11.0.6+9-jvmci-20.0-b02)
OpenJDK 64-Bit Server VM GraalVM CE 20.0.0 (build 11.0.6+9-jvmci-20.0-b02, mixed mode, sharing)
```

### (Optional) Get or build a muslC bundle required to build a static image.

Note: Static images aren't supported in [MacOS or Windows](https://github.com/oracle/graal/issues/478) . If building for those platforms skip this step

To create a truly static native image we [need to use muslC](https://github.com/oracle/graal/issues/1919#issuecomment-589085506) . Instructions and an example bundle are provided [here](https://github.com/gradinac/musl-bundle-example). For the sake of our example, we can download the resulting bundle for our build. We will need to use the path to the unpacked bundle as an argument to build the image.

```sh
wget https://github.com/gradinac/musl-bundle-example/releases/download/v1.0/musl.tar.gz -O - | tar -xz
```

### META-INF resources for reflection

We need META-INF resources whenever we use reflection. Reflection isn't used in http4s, though it is used by some logging implementations. We've provided a native image example in the [http4s giter8 template](https://github.com/http4s/http4s.g8)

#### Build an assembled jar using GraalVM

After installing the above dependencies you should build an assembled JAR. We can again use `sbt assembly` or your favorite build tool / plugin to create the assembled jar. The important thing is that we should be using the GraalVM version of Java to do so.

#### Create the native image with the assembled JAR

After we have built the assembled JAR containing all our Java dependencies, we use that JAR to build our native image. In the command below we need to replace the muslC and assembly jar paths with the appropriate locations.

Note: Mac and Windows platforms do not support build static images. Remove `--static` and `-H:UseMuslC="/path.to/muslC"` when building for those platforms.


```sh
native-image --static -H:+ReportExceptionStackTraces -H:UseMuslC="/path.to/muslC" --allow-incomplete-classpath --no-fallback --initialize-at-build-time --enable-http --enable-https --enable-all-security-services --verbose -jar "./path.to.assembly.jar" projectBinaryImage
```

A breakout for the command parameters (image generation options) :

`-H:UseMuslC` to fix DNS and related segfaults and build a true static image that doesn't depend on linked libraries.

`--initialize-at-build-time` again is related to building the static image. Again we lose performance if we instead do this at runtime.

`--enable-http`, `--enable-https` should correspond to related protocols

`--enable-all-security-services` will likely be required to make or receive https requests.

Additional image generation options are found in the [native image reference](https://www.graalvm.org/docs/reference-manual/native-image/)


#### Execute the native image

Finally we should be able to execute our output `projectBinaryImage`

```sh
./projectBinaryImage
```

At this point we may want to package the binary in a docker container, integrate the image generation to a greater build process, create init scripts via systemd, etc.

#### Why static?

As an alternative to executing via the JVM, GraalVM's native-image allows us to execute as a native binary. For example, in Linux environments this might be known as an `ELF` format. There are a number of ways to generate a native image, be it dynamic or static.

A dynamic image is less portable because it depends on shared library files on each Linux host. Then similar to creating an assembly JAR containing all our Java dependencies, when we build a static native image, we build
a more portable assembly including all the dependencies to run our binary across multiple platforms. For example we could expect a static ELF-64 binary to work across multiple linux distributions of different versions for the same architecture.
