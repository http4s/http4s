package org.http4s.server
package blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.http4s.Status._
import org.http4s.blaze._
import org.http4s.blaze.pipeline.{Command => Cmd}
import org.http4s.util.CaseInsensitiveString._
import org.specs2.mutable.Specification

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Task

class Http4sStageSpec extends Specification {
  def makeString(b: ByteBuffer): String = {
    val p = b.position()
    val a = new Array[Byte](b.remaining())
    b.get(a).position(p)
    new String(a)
  }

  def runRequest(req: Seq[String], service: HttpService): Future[ByteBuffer] = {
    val head = new SeqTestHead(req.map(s => ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII))))
    val httpStage = new Http1ServerStage(service, None) {
      override def reset(): Unit = head.stageShutdown()     // shutdown the stage after a complete request
    }
    pipeline.LeafBuilder(httpStage).base(head)
    head.sendInboundCommand(Cmd.Connected)
    head.result
  }

  "Http4sStage: Common responses" should {
    ServerTestRoutes.testRequestResults.zipWithIndex.foreach { case ((req, (status,headers,resp)), i) =>
      s"Run request $i Run request: --------\n${req.split("\r\n\r\n")(0)}\n" in {
        val result = runRequest(Seq(req), ServerTestRoutes())
        result.map(ResponseParser.apply(_)) must be_== ((status, headers, resp)).await(0, FiniteDuration(5, "seconds"))
      }
    }
  }

  "Http4sStage: Errors" should {
    val exceptionService: HttpService = {
      case r if r.uri.path == "/sync" => sys.error("Synchronous error!")
      case r if r.uri.path == "/async" => Task.fail(new Exception("Asynchronous error!"))
    }

    def runError(path: String) = runRequest(List(path), exceptionService)
        .map(ResponseParser.apply(_))
        .map{ case (s, h, r) =>
        val close = h.find{ h => h.toRaw.name == "connection".ci && h.toRaw.value == "close"}.isDefined
        (s, close, r)
      }

    "Deal with synchronous errors" in {
      val path = "GET /sync HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val result = runError(path)

      result.map{ case (s, c, r) => (s, c, r.contains("Synchronous"))} must be_== ((InternalServerError, true, true)).await
    }

    "Deal with asynchronous errors" in {
      val path = "GET /async HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val result = runError(path)

      result.map{ case (s, c, r) => (s, c, r.contains("Asynchronous"))} must be_== ((InternalServerError, true, true)).await
    }
  }
}
