import cats.effect.IO
import com.example.protos.hello._
import fs2._
import io.grpc._
import io.grpc.protobuf.services.ProtoReflectionService
import org.lyranthe.fs2_grpc.java_runtime.implicits._
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

object Main extends StreamApp[IO] {
  val helloService: ServerServiceDefinition =
    GreeterFs2Grpc.bindService(new ExampleImplementation)
  def main(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    for {
      server <- ServerBuilder
        .forPort(9999)
        .addService(helloService)
        .addService(ProtoReflectionService.newInstance())
        .stream
      _ <- IO.never
    } yield StreamApp.ExitCode.Success
  }
}
