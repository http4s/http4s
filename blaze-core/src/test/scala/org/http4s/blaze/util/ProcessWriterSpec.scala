package org.http4s
package blaze
package util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import fs2._
import fs2.Stream._
import fs2.compress.deflate
import org.http4s.blaze.TestHead
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage}
import org.http4s.util.StringWriter

class ProcessWriterSpec extends Http4sSpec {
  case object Failed extends RuntimeException

  def writeProcess(p: EntityBody)(builder: TailStage[ByteBuffer] => ProcessWriter): String = {
    val tail = new TailStage[ByteBuffer] {
      override def name: String = "TestTail"
    }

    val head = new TestHead("TestHead") {
      override def readRequest(size: Int): Future[ByteBuffer] =
        Future.failed(new Exception("Head doesn't read."))
    }

    LeafBuilder(tail).base(head)
    val w = builder(tail)

    w.writeProcess(p).unsafeRun
    head.stageShutdown()
    Await.ready(head.result, Duration.Inf)
    new String(head.getBytes(), StandardCharsets.ISO_8859_1)
  }

  val message = "Hello world!"
  val messageBuffer = Chunk.bytes(message.getBytes(StandardCharsets.ISO_8859_1))

  def runNonChunkedTests(builder: TailStage[ByteBuffer] => ProcessWriter) = {
    "Write a single emit" in {
      writeProcess(chunk(messageBuffer))(builder) must_== "Content-Length: 12\r\n\r\n" + message
    }

    "Write two emits" in {
      val p = chunk(messageBuffer) ++ chunk(messageBuffer)
      writeProcess(p.covary[Task])(builder) must_== "Content-Length: 24\r\n\r\n" + message + message
    }

    "Write an await" in {
      val p = eval(Task.delay(messageBuffer)).flatMap(chunk)
      writeProcess(p.covary[Task])(builder) must_== "Content-Length: 12\r\n\r\n" + message
    }

    "Write two awaits" in {
      val p = eval(Task.delay(messageBuffer)).flatMap(chunk)
      writeProcess(p ++ p)(builder) must_== "Content-Length: 24\r\n\r\n" + message + message
    }

    "Write a Process that fails and falls back" in {
      val p = eval(Task.fail(Failed)).onError { _ =>
        chunk(messageBuffer)
      }
      writeProcess(p)(builder) must_== "Content-Length: 12\r\n\r\n" + message
    }

    "execute cleanup processes" in {
      var clean = false
      val p = chunk(messageBuffer).onFinalize(Task.delay(clean = true))
      writeProcess(p.covary[Task])(builder) must_== "Content-Length: 12\r\n\r\n" + message
      clean must_== true
    }

    "Write tasks that repeat eval" in {
      val t = {
        var counter = 2
        Task.delay {
          counter -= 1
          if (counter >= 0) Some(Chunk.bytes("foo".getBytes(StandardCharsets.ISO_8859_1)))
          else None
        }
      }
      val p = repeatEval(t).unNoneTerminate.flatMap(chunk) ++ chunk(Chunk.bytes("bar".getBytes(StandardCharsets.ISO_8859_1)))
      writeProcess(p)(builder) must_== "Content-Length: 9\r\n\r\n" + "foofoobar"
    }
  }


  "CachingChunkWriter" should {
    runNonChunkedTests(tail => new CachingChunkWriter(new StringWriter(), tail, Task.now(Headers())))
  }

  "CachingStaticWriter" should {
    runNonChunkedTests(tail => new CachingChunkWriter(new StringWriter(), tail, Task.now(Headers())))
  }

  "ChunkProcessWriter" should {
    def builder(tail: TailStage[ByteBuffer]) =
      new ChunkProcessWriter(new StringWriter(), tail, Task.now(Headers()))

    "Write a strict chunk" in {
      // n.b. in the scalaz-stream version, we could introspect the
      // stream, note the lack of effects, and write this with a
      // Content-Length header.  In fs2, this must be chunked.
      writeProcess(chunk(messageBuffer))(builder) must_==
        """Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write two strict chunks" in {
      val p = chunk(messageBuffer) ++ chunk(messageBuffer)
      writeProcess(p.covary[Task])(builder) must_==
        """Transfer-Encoding: chunked
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
      val p = eval(Task.delay(messageBuffer)).flatMap(chunk)
      writeProcess(p.covary[Task])(builder) must_==
        """Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write two effectful chunks" in {
      val p = eval(Task.delay(messageBuffer)).flatMap(chunk)
      writeProcess(p ++ p)(builder) must_==
        """Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "Write a Process that fails and falls back" in {
      val p = eval(Task.fail(Failed)).onError { _ =>
        chunk(messageBuffer)
      }
      writeProcess(p)(builder) must_==
        """Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
    }

    "execute cleanup processes" in {
      var clean = false
      val p = chunk(messageBuffer).onFinalize {
        Task.delay(clean = true)
      }
      writeProcess(p)(builder) must_==
        """Transfer-Encoding: chunked
          |
          |c
          |Hello world!
          |0
          |
          |""".stripMargin.replaceAllLiterally("\n", "\r\n")
      clean must_== true

      clean = false
      val p2 = eval(Task.fail(Failed)).onFinalize(Task.delay{
        clean = true
      })

      writeProcess(p)(builder)
      clean must_== true
    }

    // Some tests for the raw unwinding process without HTTP encoding.
    "write a deflated stream" in {
      val p = eval(Task.delay(messageBuffer)).flatMap(chunk) through deflate()
      p.runLog.map(_.toArray) must returnValue(DumpingWriter.dump(p))
    }

    val resource = (bracket(Task.delay("foo"))({ str =>
      val it = str.iterator
      emit {
        if (it.hasNext) Some(it.next.toByte)
        else None
      }
    }, _ => Task.now(()))).unNoneTerminate

    "write a resource" in {
      val p = resource
      p.runLog.map(_.toArray) must returnValue(DumpingWriter.dump(p))
    }

    "write a deflated resource" in {
      val p = resource through deflate()
      p.runLog.map(_.toArray) must returnValue(DumpingWriter.dump(p))
    }

    "ProcessWriter must be stack safe" in {
      val p = repeatEval(Task.async[Byte]{ _(Right(0.toByte))}(Strategy.sequential)).take(300000)

      // The dumping writer is stack safe when using a trampolining EC
      (new DumpingWriter).writeProcess(p).unsafeAttemptRun must beRight
    }

    "Execute cleanup on a failing ProcessWriter" in {
      {
        var clean = false
        val p = chunk(messageBuffer).onFinalize(Task.delay {
          clean = true
        })

        (new FailingWriter().writeProcess(p).attempt.unsafeRun).isLeft must_== true
        clean must_== true
      }

      {
        var clean = false
        val p = eval(Task.fail(Failed)).onFinalize(Task.delay{
          clean = true
        })

        (new FailingWriter().writeProcess(p).attempt.unsafeRun).isLeft must_== true
        clean must_== true
      }

    }
  }
}
