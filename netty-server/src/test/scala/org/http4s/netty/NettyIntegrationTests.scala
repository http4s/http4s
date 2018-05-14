package org.http4s
package netty

import cats.effect.IO
import cats.implicits._
import fs2.{Pipe, Pull, Stream}
import io.netty.util.ResourceLeakDetector
import org.http4s.client.blaze._
import org.http4s.dsl.io._
import org.http4s.multipart._
import org.specs2.specification.AfterAll
class NettyIntegrationTests extends Http4sSpec with AfterAll {

  val service: HttpService[IO] = HttpService {
    case GET -> Root / "ping" =>
      Ok()
    case r @ POST -> Root / "consume" =>
      r.body.compile.toList.flatMap(l => Ok(l.length.toString))
    case GET -> Root / "simple" =>
      Ok("waylonnn jenningssss")
    case HEAD -> Root / "head" =>
      Ok("LOLOLOL")
    case r @ POST -> Root / "proxy" =>
      IO(Response[IO](Ok, headers = r.headers, body = r.body))
    case POST -> Root / "syncError" =>
      throw new NullPointerException("badabing")
    case POST -> Root / "asyncError" =>
      IO.raiseError(new NullPointerException("badaboom"))
    case GET -> Root / "longbody" =>
      Ok().map(
        _.withBodyStream(Stream.emits(List[Byte](1, 2, 3, 4, 5)).covary[IO].repeat.take(10000)))
  }

  val server = NettyBuilder[IO].mountService(service).start.unsafeRunSync()

  val client = Http1Client[IO]().unsafeRunSync

  def failStreamAt[A](count: Int): Pipe[IO, A, A] = {
    def go(currCount: Int, current: Stream[IO, A]): Pull[IO, A, Unit] =
      if (currCount == count) Pull.done
      else
        current.pull.uncons1.flatMap {
          case Some((_, rest)) => go(currCount + 1, rest)
          case None => Pull.done
        }

    stream =>
      go(0, stream).stream
  }

  //Panic on a bytebuf not cleaned up. It won't give us a test error,
  //but it will essentially
  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)

  sequential ^ ("Http4s Netty server integration test" should {

    "Properly return the ping response" in {
      client.get(server.baseUri / "ping")(r => IO(r.status)).unsafeRunSync() must_== Ok
    }

    "Return the proper content length for a simple response" in {
      val length = "waylonnn jenningssss".length
      client
        .get(server.baseUri / "simple")(r => IO(r.contentLength.getOrElse(0)))
        .unsafeRunSync() must_== length
    }

    "Consume an entire body properly in " in {
      val requestBody = "IAMGROOOOOOT"
      val requestStream = Stream.emit(requestBody).covary[IO].through(fs2.text.utf8Encode[IO])
      client
        .expect[String](Request[IO](POST, server.baseUri / "consume", body = requestStream))
        .unsafeRunSync()
        .toInt must_== requestBody.length
    }

    "Return the proper content length but no body for HEAD" in {
      val length = "LOLOLOL".length
      client
        .fetch(Request[IO](HEAD, server.baseUri / "head"))(r =>
          r.body.compile.toList.map(b => (b.length, r.contentLength.getOrElse(0))))
        .unsafeRunSync() must_== ((0, length))
    }

    "Proxy a request back properly" in {
      val multipart =
        Multipart[IO](Vector(Part.formData("hi", "hello"), Part.formData("beep", "boop")))
      val program = for {
        req <- Request[IO](POST, server.baseUri / "proxy")
          .withBody(multipart)
        mp <- client
          .expect[Multipart[IO]](req.replaceAllHeaders(multipart.headers))
      } yield mp.parts(1).name

      program.unsafeRunSync() must beSome("beep")
    }

    "Handle an uncaught synchronous exception in the default error handler" in {
      client
        .fetch(Request[IO](POST, server.baseUri / "syncError"))(IO.pure)
        .unsafeRunSync()
        .status must_== InternalServerError
    }

    "Handle an uncaught asynchronous exception in the default error handler" in {
      client
        .fetch(Request[IO](POST, server.baseUri / "asyncError"))(IO.pure)
        .unsafeRunSync()
        .status must_== InternalServerError
    }

    "Work normally for a partially fetched body" in {
      client
        .fetch(Request[IO](GET, server.baseUri / "longbody"))(
          r => r.body.take(1).compile.drain.attempt >> IO.pure(r.status)
        )
        .unsafeRunSync() must_== Ok
    }

  })

  override def afterAll(): Unit = {
    server.shutdown.attempt.unsafeRunSync()
    client.shutdown.attempt.unsafeRunSync()
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED)
    ()
  }
}
