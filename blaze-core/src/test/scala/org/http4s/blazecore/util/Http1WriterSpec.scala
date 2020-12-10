/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package blazecore
package util

import cats.effect._
import cats.implicits._
import fs2._
import fs2.Stream._
import fs2.compression.{DeflateParams, deflate}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import cats.effect.std.Dispatcher
import cats.effect.testing.specs2.CatsEffect
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage}
import org.http4s.util.StringWriter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

class Http1WriterSpec extends Http4sSpec with CatsEffect {
  case object Failed extends RuntimeException

  final def writeEntityBody(p: EntityBody[IO])(
      builder: TailStage[ByteBuffer] => Http1Writer[IO]): IO[String] = {
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

  val message = "Hello world!"
  val messageBuffer = Chunk.bytes(message.getBytes(StandardCharsets.ISO_8859_1))

  final def runNonChunkedTests(
      builder: Dispatcher[IO] => TailStage[ByteBuffer] => Http1Writer[IO]) =
    withResource(Dispatcher[IO]) { implicit dispatcher =>
      "Write a single emit" in {
        writeEntityBody(chunk(messageBuffer))(builder(dispatcher))
          .map(_ must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
      }

      "Write two emits" in {
        val p = chunk(messageBuffer) ++ chunk(messageBuffer)
        writeEntityBody(p.covary[IO])(builder(dispatcher))
          .map(
            _ must_== "Content-Type: text/plain\r\nContent-Length: 24\r\n\r\n" + message + message)
      }

      "Write an await" in {
        val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
        writeEntityBody(p)(builder(dispatcher))
          .map(_ must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
      }

      "Write two awaits" in {
        val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
        writeEntityBody(p ++ p)(builder(dispatcher))
          .map(
            _ must_== "Content-Type: text/plain\r\nContent-Length: 24\r\n\r\n" + message + message)
      }

      "Write a body that fails and falls back" in {
        val p = eval(IO.raiseError(Failed)).handleErrorWith { _ =>
          chunk(messageBuffer)
        }
        writeEntityBody(p)(builder(dispatcher))
          .map(_ must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
      }

      "execute cleanup" in (for {
        clean <- Ref.of[IO, Boolean](false)
        p = chunk(messageBuffer).covary[IO].onFinalizeWeak(clean.set(true))
        _ <- writeEntityBody(p)(builder(dispatcher))
          .map(_ must_== "Content-Type: text/plain\r\nContent-Length: 12\r\n\r\n" + message)
        _ <- clean.get.map(_ must beTrue)
      } yield ok)

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
        writeEntityBody(p)(builder(dispatcher))
          .map(_ must_== "Content-Type: text/plain\r\nContent-Length: 9\r\n\r\n" + "foofoobar")
      }
    }

  "CachingChunkWriter" should {
    runNonChunkedTests(implicit dispatcher =>
      tail => new CachingChunkWriter[IO](tail, IO.pure(Headers.empty), 1024 * 1024))
  }

  "CachingStaticWriter" should {
    runNonChunkedTests(implicit dispatcher =>
      tail => new CachingChunkWriter[IO](tail, IO.pure(Headers.empty), 1024 * 1024))
  }

  "FlushingChunkWriter" should {
    withResource(Dispatcher[IO]) { implicit dispatcher =>
      def builder(tail: TailStage[ByteBuffer]): FlushingChunkWriter[IO] =
        new FlushingChunkWriter[IO](tail, IO.pure(Headers.empty))

      "Write a strict chunk" in {
        // n.b. in the scalaz-stream version, we could introspect the
        // stream, note the lack of effects, and write this with a
        // Content-Length header.  In fs2, this must be chunked.
        writeEntityBody(chunk(messageBuffer))(builder).map(_ must_==
          """Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |c
            |Hello world!
            |0
            |
            |""".stripMargin.replace("\n", "\r\n"))
      }

      "Write two strict chunks" in {
        val p = chunk(messageBuffer) ++ chunk(messageBuffer)
        writeEntityBody(p.covary[IO])(builder).map(_ must_==
          """Content-Type: text/plain
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

      "Write an effectful chunk" in {
        // n.b. in the scalaz-stream version, we could introspect the
        // stream, note the chunk was followed by halt, and write this
        // with a Content-Length header.  In fs2, this must be chunked.
        val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
        writeEntityBody(p)(builder).map(_ must_==
          """Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |c
            |Hello world!
            |0
            |
            |""".stripMargin.replace("\n", "\r\n"))
      }

      "Write two effectful chunks" in {
        val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
        writeEntityBody(p ++ p)(builder).map(_ must_==
          """Content-Type: text/plain
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

      "Elide empty chunks" in {
        // n.b. We don't do anything special here.  This is a feature of
        // fs2, but it's important enough we should check it here.
        val p: Stream[IO, Byte] = chunk(Chunk.empty) ++ chunk(messageBuffer)
        writeEntityBody(p.covary[IO])(builder).map(_ must_==
          """Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |c
            |Hello world!
            |0
            |
            |""".stripMargin.replace("\n", "\r\n"))
      }

      "Write a body that fails and falls back" in {
        val p = eval(IO.raiseError(Failed)).handleErrorWith { _ =>
          chunk(messageBuffer)
        }
        writeEntityBody(p)(builder).map(_ must_==
          """Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |c
            |Hello world!
            |0
            |
            |""".stripMargin.replace("\n", "\r\n"))
      }

      "execute cleanup" in (for {
        clean <- Ref.of[IO, Boolean](false)
        p = chunk(messageBuffer).onFinalizeWeak(clean.set(true))
        _ <- writeEntityBody(p)(builder).map(_ must_==
          """Content-Type: text/plain
            |Transfer-Encoding: chunked
            |
            |c
            |Hello world!
            |0
            |
            |""".stripMargin.replace("\n", "\r\n"))
        _ <- clean.get.map(_ must beTrue)
        _ <- clean.set(false)
        p2 = eval(IO.raiseError(new RuntimeException("asdf"))).onFinalizeWeak(clean.set(true))
        _ <- writeEntityBody(p2)(builder)
        _ <- clean.get.map(_ must beTrue)
      } yield ok)

      // Some tests for the raw unwinding body without HTTP encoding.
      "write a deflated stream" in {
        val s = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])
        val p = s.through(deflate(DeflateParams.DEFAULT))
        (
          p.compile.toVector.map(_.toArray),
          DumpingWriter.dump(s.through(deflate(DeflateParams.DEFAULT)))).mapN(_ === _)
      }

      val resource: Stream[IO, Byte] =
        bracket(IO("foo"))(_ => IO.unit).flatMap { str =>
          val it = str.iterator
          emit {
            if (it.hasNext) Some(it.next().toByte)
            else None
          }
        }.unNoneTerminate

      "write a resource" in {
        val p = resource
        (p.compile.toVector.map(_.toArray), DumpingWriter.dump(p)).mapN(_ === _)
      }

      "write a deflated resource" in {
        val p = resource.through(deflate(DeflateParams.DEFAULT))
        (
          p.compile.toVector.map(_.toArray),
          DumpingWriter.dump(resource.through(deflate(DeflateParams.DEFAULT))))
          .mapN(_ === _)
      }

      "must be stack safe" in {
        val p = repeatEval(IO.pure[Byte](0.toByte)).take(300000)

        // The dumping writer is stack safe when using a trampolining EC
        (new DumpingWriter).writeEntityBody(p).attempt.map(_ must beRight)
      }

      "Execute cleanup on a failing Http1Writer" in (for {
        clean <- Ref.of[IO, Boolean](false)
        p = chunk(messageBuffer).onFinalizeWeak(clean.set(true))
        _ <- new FailingWriter().writeEntityBody(p).attempt.map(_ must beLeft)
        _ <- clean.get.map(_ must_== true)
      } yield ok)

      "Execute cleanup on a failing Http1Writer with a failing process" in (for {
        clean <- Ref.of[IO, Boolean](false)
        p = eval(IO.raiseError(Failed)).onFinalizeWeak(clean.set(true))
        _ <- new FailingWriter().writeEntityBody(p).attempt.map(_ must beLeft)
        _ <- clean.get.map(_ must_== true)
      } yield ok)

      "Write trailer headers" in {
        def builderWithTrailer(tail: TailStage[ByteBuffer]): FlushingChunkWriter[IO] =
          new FlushingChunkWriter[IO](
            tail,
            IO.pure(Headers.of(Header("X-Trailer", "trailer header value"))))

        val p = eval(IO(messageBuffer)).flatMap(chunk(_).covary[IO])

        writeEntityBody(p)(builderWithTrailer).map(_ must_===
          """Content-Type: text/plain
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
  }
}
