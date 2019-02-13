package org.http4s
package blazecore
package util

import cats.effect._
import fs2._
import fs2.Stream._
import fs2.compress.deflate
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage}
import org.http4s.util.StringWriter
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class Http1WriterSpec extends Http4sSpec {
  case object Failed extends RuntimeException

  final def writeEntityBody(p: EntityBody[IO])(
      builder: TailStage[ByteBuffer] => Http1Writer[IO]): String = {
    val tail = new TailStage[ByteBuffer] {
      override def name: String = "TestTail"
    }

    val head = new TestHead("TestHead") {
      override def readRequest(size: Int): Future[ByteBuffer] =
        Future.failed(new Exception("Head doesn't read."))
    }

    LeafBuilder(tail).base(head)
    val w = builder(tail)

    (for {
      _ <- IO.fromFuture(IO(w.writeHeaders(new StringWriter << "Content-Type: text/plain\r\n")))
      _ <- w.writeEntityBody(p).attempt
    } yield ()).unsafeRunSync()
    head.stageShutdown()
    Await.ready(head.result, Duration.Inf)
    new String(head.getBytes(), StandardCharsets.ISO_8859_1)
  }

  val message = "Hello world!"
  val messageBuffer = Chunk.bytes(message.getBytes(StandardCharsets.ISO_8859_1))

  final def runNonChunkedTests(builder: TailStage[ByteBuffer] => Http1Writer[IO]) = {
    "Write a single emit" in {
      writeEntityBody(chunk(messageBuffer))(builder) must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message
    }

    "Write two emits" in {
      val p = chunk(messageBuffer) ++ chunk(messageBuffer)
      writeEntityBody(p.covary[IO])(builder) must_== "Content-Type: text/plain\r\nContent-Length: 24\r\n\r\n" + message + message
    }

    "Write an await" in {
      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      writeEntityBody(p)(builder) must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message
    }

    "Write two awaits" in {
      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      writeEntityBody(p ++ p)(builder) must_== "Content-Type: text/plain\r\nContent-Length: 24\r\n\r\n" + message + message
    }

    "Write a body that fails and falls back" in {
      val p = eval(IO.raiseError(Failed)).handleErrorWith { _ =>
        chunk(messageBuffer)
      }
      writeEntityBody(p)(builder) must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message
    }

    "execute cleanup" in {
      var clean = false
      val p = chunk(messageBuffer).covary[IO].onFinalize(IO { clean = true; () })
      writeEntityBody(p)(builder) must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message
      clean must_== true
    }

    "Write tasks that repeat eval" in {
      val t = {
        var counter = 2
        IO {
          counter -= 1
          if (counter >= 0) Some(Chunk.bytes("foo".getBytes(StandardCharsets.ISO_8859_1)))
          else None
        }
      }
      val p = repeatEval(t).unNoneTerminate.flatMap(chunk(_).covary[IO]) ++ chunk(
        Chunk.bytes("bar".getBytes(StandardCharsets.ISO_8859_1)))
      writeEntityBody(p)(builder) must_== "Content-Type: text/plain\r\nContent-Length: 9\r\n\r\n" + "foofoobar"
    }
  }

  "CachingChunkWriter" should {
    runNonChunkedTests(tail => new CachingChunkWriter[IO](tail, IO.pure(Headers()), 1024 * 1024))
  }

  "CachingStaticWriter" should {
    runNonChunkedTests(tail => new CachingChunkWriter[IO](tail, IO.pure(Headers()), 1024 * 1024))
  }

  "FlushingChunkWriter" should {

    def builder(tail: TailStage[ByteBuffer]): FlushingChunkWriter[IO] =
      new FlushingChunkWriter[IO](tail, IO.pure(Headers()))

    "Write a strict chunk" in {
      // n.b. in the scalaz-stream version, we could introspect the
      // stream, note the lack of effects, and write this with a
      // Content-Length header.  In fs2, this must be chunked.
      writeEntityBody(chunk(messageBuffer))(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write two strict chunks" in {
      val p = chunk(messageBuffer) ++ chunk(messageBuffer)
      writeEntityBody(p.covary[IO])(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write an effectful chunk" in {
      // n.b. in the scalaz-stream version, we could introspect the
      // stream, note the chunk was followed by halt, and write this
      // with a Content-Length header.  In fs2, this must be chunked.
      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      writeEntityBody(p)(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write two effectful chunks" in {
      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      writeEntityBody(p ++ p)(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Elide empty chunks" in {
      // n.b. We don't do anything special here.  This is a feature of
      // fs2, but it's important enough we should check it here.
      val p: Stream[IO, Byte] = chunk(Chunk.empty) ++ chunk(messageBuffer)
      writeEntityBody(p.covary[IO])(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write a body that fails and falls back" in {
      val p = eval(IO.raiseError(Failed)).handleErrorWith { _ =>
        chunk(messageBuffer)
      }
      writeEntityBody(p)(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "execute cleanup" in {
      var clean = false
      val p = chunk(messageBuffer).onFinalize(IO { clean = true; () })
      writeEntityBody(p)(builder) must_==
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
      clean must_== true

      clean = false
      val p2 = eval(IO.raiseError(new RuntimeException("asdf"))).onFinalize(IO { clean = true; () })
      writeEntityBody(p2)(builder)
      clean must_== true
    }

    // Some tests for the raw unwinding body without HTTP encoding.
    "write a deflated stream" in {
      val s = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      val p = s.through(deflate())
      p.compile.toVector.map(_.toArray) must returnValue(DumpingWriter.dump(s.through(deflate())))
    }

    val resource: Stream[IO, Byte] =
      bracket(IO("foo"))(_ => IO.unit).flatMap { str =>
        val it = str.iterator
        emit {
          if (it.hasNext) Some(it.next.toByte)
          else None
        }
      }.unNoneTerminate

    "write a resource" in {
      val p = resource
      p.compile.toVector.map(_.toArray) must returnValue(DumpingWriter.dump(p))
    }

    "write a deflated resource" in {
      val p = resource.through(deflate())
      p.compile.toVector.map(_.toArray) must returnValue(
        DumpingWriter.dump(resource.through(deflate())))
    }

    "must be stack safe" in {
      val p = repeatEval(IO.async[Byte](_(Right(0.toByte)))).take(300000)

      // The dumping writer is stack safe when using a trampolining EC
      (new DumpingWriter).writeEntityBody(p).attempt.unsafeRunSync must beRight
    }

    "Execute cleanup on a failing Http1Writer" in {
      {
        var clean = false
        val p = chunk(messageBuffer).onFinalize(IO { clean = true; () })

        new FailingWriter().writeEntityBody(p).attempt.unsafeRunSync() must beLeft
        clean must_== true
      }

      {
        var clean = false
        val p = eval(IO.raiseError(Failed)).onFinalize(IO { clean = true; () })

        new FailingWriter().writeEntityBody(p).attempt.unsafeRunSync must beLeft
        clean must_== true
      }
    }

    "Write trailer headers" in {
      def builderWithTrailer(tail: TailStage[ByteBuffer]): FlushingChunkWriter[IO] =
        new FlushingChunkWriter[IO](
          tail,
          IO.pure(Headers(Header("X-Trailer", "trailer header value"))))

      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])

      writeEntityBody(p)(builderWithTrailer) must_===
        """Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |X-Trailer: trailer header value
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")

    }
  }
}
