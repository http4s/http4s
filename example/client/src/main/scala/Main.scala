import cats.effect.IO
import com.example.protos.hello._
import fs2._
import io.grpc._

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends StreamApp[IO] {
  val managedChannelStream: Stream[IO, ManagedChannel] =
    Stream.bracket(
      IO(
        ManagedChannelBuilder
          .forAddress("127.0.0.1", 9999)
          .usePlaintext()
          .build()))(Stream.emit[ManagedChannel],
                     (channel: ManagedChannel) => IO(channel.shutdown()))

  def runProgram(helloStub: GreeterFs2Grpc[IO]): IO[Unit] = {
    for {
      response <- helloStub.sayHello(HelloRequest("John Doe"), new Metadata())
      _ <- IO(println(response.message))
    } yield ()
  }

  override def stream(
      args: List[String],
      requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    for {
      managedChannel <- managedChannelStream
      helloStub = GreeterFs2Grpc.stub[IO](managedChannel)
      _ <- Stream.eval(runProgram(helloStub))
    } yield StreamApp.ExitCode.Success
  }
}
