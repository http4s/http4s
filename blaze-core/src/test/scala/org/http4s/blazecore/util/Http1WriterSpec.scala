/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blazecore
package util

import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import fs2.Stream._
import fs2._
import fs2.compression.deflate
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter
import org.typelevel.ci._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class Http1WriterSpec extends Http4sSuite {
  implicit val ec: ExecutionContext = Http4sSuite.TestExecutionContext

  case object Failed extends RuntimeException

  final def writeEntityBody(
      p: EntityBody[IO]
  )(builder: TailStage[ByteBuffer] => Http1Writer[IO]): IO[String] = {
    val tail = new TailStage[ByteBuffer] {
      override def name: String = "TestTail"
    }

    val head = new TestHead("TestHead") {
      override def readRequest(size: Int): Future[ByteBuffer] =
        Future.failed(new Exception("Head doesn't read."))
    }

    LeafBuilder(tail).base(head)
    val w = builder(tail)

    for {
      _ <- IO.fromFuture(IO(w.writeHeaders(new StringWriter << "Content-Type: text/plain\r\n")))
      _ <- w.writeEntityBody(p).attempt
      _ <- IO(head.stageShutdown())
      _ <- IO.fromFuture(IO(head.result))
    } yield new String(head.getBytes(), StandardCharsets.ISO_8859_1)
  }

  private val message = "Hello world!"
  private val messageBuffer = Chunk.bytes(message.getBytes(StandardCharsets.ISO_8859_1))

  final def runNonChunkedTests(
      name: String,
      builder: TailStage[ByteBuffer] => Http1Writer[IO],
  ): Unit = {
    test(s"$name Write a single emit") {
      writeEntityBody(chunk(messageBuffer))(builder)
        .assertEquals("Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
    }

    test(s"$name Write two emits") {
      val p = chunk(messageBuffer) ++ chunk(messageBuffer)
      writeEntityBody(p.covary[IO])(builder)
        .assertEquals("Content-Type: text/plain\r\nContent-Length: 24\r\n\r\n" + message + message)
    }

    test(s"$name Write an await") {
      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      writeEntityBody(p)(builder)
        .assertEquals("Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
    }

    test(s"$name Write two awaits") {
      val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
      writeEntityBody(p ++ p)(builder)
        .assertEquals("Content-Type: text/plain\r\nContent-Length: 24\r\n\r\n" + message + message)
    }

    test(s"$name Write a body that fails and falls back") {
      val p = eval(IO.raiseError(Failed)).handleErrorWith { _ =>
        chunk(messageBuffer)
      }
      writeEntityBody(p)(builder)
        .assertEquals("Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
    }

    test(s"$name execute cleanup") {
      (for {
        clean <- Ref.of[IO, Boolean](false)
        p = chunk(messageBuffer).covary[IO].onFinalizeWeak(clean.set(true))
        r <- writeEntityBody(p)(builder)
          .map(_ == "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
        c <- clean.get
      } yield r && c).assert
    }

    test(s"$name Write tasks that repeat eval") {
      val t = {
        var counter = 2
        IO {
          counter -= 1
          if (counter >= 0) Some(Chunk.bytes("foo".getBytes(StandardCharsets.ISO_8859_1)))
          else None
        }
      }
      val p = repeatEval(t).unNoneTerminate.flatMap(chunk(_).covary[IO]) ++ chunk(
        Chunk.bytes("bar".getBytes(StandardCharsets.ISO_8859_1))
      )
      writeEntityBody(p)(builder)
        .assertEquals("Content-Type: text/plain\r\nContent-Length: 9\r\n\r\n" + "foofoobar")
    }
  }

  runNonChunkedTests(
    "CachingChunkWriter",
    tail => new CachingChunkWriter[IO](tail, IO.pure(Headers.empty), 1024 * 1024, false),
  )

  runNonChunkedTests(
    "CachingStaticWriter",
    tail => new CachingChunkWriter[IO](tail, IO.pure(Headers.empty), 1024 * 1024, false),
  )

  def builder(tail: TailStage[ByteBuffer]): FlushingChunkWriter[IO] =
    new FlushingChunkWriter[IO](tail, IO.pure(Headers.empty))

  test("FlushingChunkWriter should Write a strict chunk") {
    // n.b. in the scalaz-stream version, we could introspect the
    // stream, note the lack of effects, and write this with a
    // Content-Length header.  In fs2, this must be chunked.
    writeEntityBody(chunk(messageBuffer))(builder).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

  test("FlushingChunkWriter should Write two strict chunks") {
    val p = chunk(messageBuffer) ++ chunk(messageBuffer)
    writeEntityBody(p.covary[IO])(builder).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

  test("FlushingChunkWriter should Write an effectful chunk") {
    // n.b. in the scalaz-stream version, we could introspect the
    // stream, note the chunk was followed by halt, and write this
    // with a Content-Length header.  In fs2, this must be chunked.
    val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
    writeEntityBody(p)(builder).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

  test("FlushingChunkWriter should Write two effectful chunks") {
    val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
    writeEntityBody(p ++ p)(builder).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

  test("FlushingChunkWriter should Elide empty chunks") {
    // n.b. We don't do anything special here.  This is a feature of
    // fs2, but it's important enough we should check it here.
    val p: Stream[IO, Byte] = chunk(Chunk.empty) ++ chunk(messageBuffer)
    writeEntityBody(p.covary[IO])(builder).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

  test("FlushingChunkWriter should Write a body that fails and falls back") {
    val p = eval(IO.raiseError(Failed)).handleErrorWith { _ =>
      chunk(messageBuffer)
    }
    writeEntityBody(p)(builder).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

  test("FlushingChunkWriter should execute cleanup") {
    (for {
      clean <- Ref.of[IO, Boolean](false)
      p = chunk(messageBuffer).onFinalizeWeak(clean.set(true))
      w <- writeEntityBody(p)(builder).map(
        _ ==
          """Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |c
            |Hello world!
            |0
            |
            |""".stripMargin.replace("\n", "\r\n")
      )
      c <- clean.get
      _ <- clean.set(false)
      p2 = eval(IO.raiseError(new RuntimeException("asdf"))).onFinalizeWeak(clean.set(true))
      _ <- writeEntityBody(p2)(builder)
      c2 <- clean.get
    } yield w && c && c2).assert
  }

  // Some tests for the raw unwinding body without HTTP encoding.
  test("FlushingChunkWriter should write a deflated stream") {
    val s = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
    val p = s.through(deflate())
    (p.compile.toVector.map(_.toArray), DumpingWriter.dump(s.through(deflate())))
      .mapN(_ sameElements _)
      .assert
  }

  val resource: Stream[IO, Byte] =
    bracket(IO("foo"))(_ => IO.unit).flatMap { str =>
      val it = str.iterator
      emit {
        if (it.hasNext) Some(it.next().toByte)
        else None
      }
    }.unNoneTerminate

  test("FlushingChunkWriter should write a resource") {
    val p = resource
    (p.compile.toVector.map(_.toArray), DumpingWriter.dump(p)).mapN(_ sameElements _).assert
  }

  test("FlushingChunkWriter should write a deflated resource") {
    val p = resource.through(deflate())
    (p.compile.toVector.map(_.toArray), DumpingWriter.dump(resource.through(deflate())))
      .mapN(_ sameElements _)
      .assert
  }

  test("FlushingChunkWriter should must be stack safe") {
    val p = repeatEval(IO.async[Byte](_(Right(0.toByte)))).take(300000)

    // The dumping writer is stack safe when using a trampolining EC
    (new DumpingWriter).writeEntityBody(p).attempt.map(_.isRight).assert
  }

  test("FlushingChunkWriter should Execute cleanup on a failing Http1Writer") {
    (for {
      clean <- Ref.of[IO, Boolean](false)
      p = chunk(messageBuffer).onFinalizeWeak(clean.set(true))
      w <- new FailingWriter().writeEntityBody(p).attempt
      c <- clean.get
    } yield w.isLeft && c).assert
  }

  test(
    "FlushingChunkWriter should Execute cleanup on a failing Http1Writer with a failing process"
  ) {
    (for {
      clean <- Ref.of[IO, Boolean](false)
      p = eval(IO.raiseError(Failed)).onFinalizeWeak(clean.set(true))
      w <- new FailingWriter().writeEntityBody(p).attempt
      c <- clean.get
    } yield w.isLeft && c).assert
  }

  test("FlushingChunkWriter should Write trailer headers") {
    def builderWithTrailer(tail: TailStage[ByteBuffer]): FlushingChunkWriter[IO] =
      new FlushingChunkWriter[IO](
        tail,
        IO.pure(Headers(Header.Raw(ci"X-Trailer", "trailer header value"))),
      )

    val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])

    writeEntityBody(p)(builderWithTrailer).assertEquals("""Content-Type: text/plain
          |Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |X-Trailer: trailer header value
          |
          |""".stripMargin.replace("\n", "\r\n"))
  }

}
