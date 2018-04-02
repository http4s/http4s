import cats.effect.IO
import com.example.protos.hello._
import fs2._
import io.grpc._

import scala.concurrent.ExecutionContext.Implicits.global

class ExampleImplementation extends GreeterFs2Grpc[IO] {
  override def sayHello(request: HelloRequest,
                        clientHeaders: Metadata): IO[HelloReply] = {
    IO(HelloReply("Request name is: " + request.name))
  }

  override def sayHelloStream(
      request: Stream[IO, HelloRequest],
      clientHeaders: Metadata): Stream[IO, HelloReply] = {
    request.evalMap(req => sayHello(req, clientHeaders))
  }
}

object Main {
  val helloService: ServerServiceDefinition =
    GreeterFs2Grpc.bindService(new ExampleImplementation)
  val server: Server =
    ServerBuilder.forPort(9999).addService(helloService).build()

  val serverStream: Stream[IO, Server] =
    Stream.bracket(IO(server.start()))(
      Stream.emit[Server],
      (server: Server) => IO(server.shutdown()))

  def main(args: Array[String]): Unit = {
    server.start()
    server.awaitTermination()
  }
}
