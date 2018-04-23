# fs2-grpc - gRPC implementation for FS2/cats-effect

[![Join the chat at https://gitter.im/fs2-grpc/Lobby](https://badges.gitter.im/fs2-grpc/Lobby.svg)](https://gitter.im/fs2-grpc/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## SBT configuration

`project/plugins.sbt`:
```scala
addSbtPlugin("org.lyranthe.fs2-grpc" % "sbt-java-gen" % "0.3.0")
```

`build.sbt`:
```scala
enablePlugins(Fs2Grpc)
```

## Protocol buffer files

The protobuf files should be stored in the directory `<project_root>/src/main/protobuf`.

## Multiple projects

If the generated code is used by multiple projects, you may build the client/server code in a common project which other projects depend on. For example:

```scala
lazy val protobuf =
  project
    .in(file("protobuf"))
    .enablePlugins(Fs2Grpc)

lazy val client =
  project
    .in(file("client"))
    .dependsOn(protobuf)

lazy val server =
  project
    .in(file("server"))
    .dependsOn(protobuf)
```

## Creating a client

A `ManagedChannel` is the type used by `grpc-java` to manage a connection to a particular server. This library provides syntax for `ManagedChannelBuilder` which creates an FS2 `Stream` which can manage the shutdown of the channel, by calling `.stream[F]` where `F` has an instance of the `Sync` typeclass. This implementation will do a drain of the server, and attempt to shut down the server, forcefully closing after 30 seconds. An example of the syntax is:

```scala
val managedChannelStream: Stream[IO, ManagedChannel] =
  ManagedChannelBuilder
    .forAddress("127.0.0.1", 9999)
    .stream[IO]
```

The syntax also offers the method `streamWithShutdown` which takes a function `ManagedChannel => F[Unit]` which is used to manage the shutdown. This may be used where requirements before shutdown do not match the default behaviour.

The generated code provides a method `stub[F]` (for any `F` which has a `Sync` instance), and it takes a parameter of type `ManagedChannel`. It returns an implementation of the service (in a trait), which can be used to make calls.

```scala
def runProgram(stub: MyFs2Grpc[IO]): IO[Unit] = ???

for {
  managedChannel <- managedChannelStream
  client = MyFs2Grpc.stub[IO](managedChannel)
  _ <- Stream.eval(runProgram(client))
} yield ()
```

## Creating a server

The generated code provides a method `bindService[F]` (for any `F` which has a `Sync` instance), and it takes an implementation of the service (in a trait), which is used to serve responses to RPC calls. It returns a `ServerServiceDefinition` which is given to the server builder when setting up the service.

A `Server` is the type used by `grpc-java` to manage the server connections and lifecycle. This library provides syntax for `ServerBuilder`, which mirrors the pattern for the client. An example is:

```scala
val helloService: ServerServiceDefinition = MyFs2Grpc.bindService(new MyImpl())

ServerBuilder
  .forPort(9999)
  .addService(helloService)
  .addService(ProtoReflectionService.newInstance()) // reflection makes lots of tooling happy
  .stream[IO] // or for any F: Sync
  .evalMap(server => IO(server.start())) // start server
  .evalMap(_ => IO.never) // server now running
```

## Code generation options

To alter code generation, you can set some flags with `scalapbCodeGeneratorOptions`, e.g.:

```scala
scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage
```

The full set of options available are:

 - `CodeGeneratorOption.FlatPackage` - If true, the compiler does not append the proto base file name
 - `CodeGeneratorOption.JavaConversions` - Enable Java conversions for protobuf
 - `CodeGeneratorOption.Grpc` (included by default) - generate grpc bindings based on Observables
 - `CodeGeneratorOption.Fs2Grpc` (included by default) - generate grpc bindings for FS2/cats
 - `CodeGeneratorOption.SingleLineToProtoString` - `toProtoString` generates single line
 - `CodeGeneratorOption.AsciiFormatToString` - `toString` uses `toProtoString` functionality
