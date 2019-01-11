package org.http4s

import java.io.{File, FileInputStream, InputStreamReader}
import java.nio.charset.StandardCharsets

import cats.effect._
import cats.implicits._
import fs2.Stream._
import fs2._
import org.http4s.Status.Ok
// import org.http4s.util.execution.trampoline
import org.specs2.execute.PendingUntilFixed

// import scala.concurrent.ExecutionContext

class JVMEntityDecoderSpec extends Http4sSpec with PlatformHttp4sSpec with PlatformEntityDecoderInstances with PendingUntilFixed {

  def getBody(body: EntityBody[IO]): IO[Array[Byte]] =
    body.compile.toVector.map(_.toArray)

  def strBody(body: String): Stream[IO, Byte] =
    chunk(Chunk.bytes(body.getBytes(StandardCharsets.UTF_8)))

  "A File EntityDecoder" should {
    val binData: Array[Byte] = "Bytes 10111".getBytes

    def readFile(in: File): Array[Byte] = {
      val os = new FileInputStream(in)
      val data = new Array[Byte](in.length.asInstanceOf[Int])
      os.read(data)
      data
    }

    def readTextFile(in: File): String = {
      val os = new InputStreamReader(new FileInputStream(in))
      val data = new Array[Char](in.length.asInstanceOf[Int])
      os.read(data, 0, in.length.asInstanceOf[Int])
      data.foldLeft("")(_ + _)
    }

    def mockServe(req: Request[IO])(route: Request[IO] => IO[Response[IO]]) =
      route(req.withBodyStream(chunk(Chunk.bytes(binData))))

    "Write a text file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mockServe(Request()) { req =>
          req.decodeWith(
            EntityDecoder.textFile(tmpFile, testBlockingExecutionContext),
            strict = false) { _ =>
            Response[IO](Ok).withEntity("Hello").pure[IO]
          }
        }.unsafeRunSync

        readTextFile(tmpFile) must_== new String(binData)
        response.status must_== Status.Ok
        getBody(response.body) must returnValue("Hello".getBytes)
      } finally {
        tmpFile.delete()
        ()
      }
    }

    "Write a binary file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mockServe(Request()) {
          case req =>
            req.decodeWith(
              EntityDecoder.binFile(tmpFile, testBlockingExecutionContext),
              strict = false) { _ =>
              Response[IO](Ok).withEntity("Hello").pure[IO]
            }
        }.unsafeRunSync

        response must beStatus(Status.Ok)
        getBody(response.body) must returnValue("Hello".getBytes)
        readFile(tmpFile) must_== binData
      } finally {
        tmpFile.delete()
        ()
      }
    }
  }
}
