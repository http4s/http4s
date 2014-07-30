package org.http4s.server.blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.http4s.blaze._
import org.http4s.blaze.pipeline.{Command => Cmd}
import org.specs2.mutable.Specification

import scala.concurrent.Future

class Http4sStageSpec extends Specification {
  def makeString(b: ByteBuffer): String = {
    val p = b.position()
    val a = new Array[Byte](b.remaining())
    b.get(a).position(p)
    new String(a)
  }

  def runRequest(req: Seq[String]): Future[ByteBuffer] = {
    val head = new SeqTestHead(req.map(s => ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII))))
    val httpStage = new Http1ServerStage(ServerTestRoutes(), None) {
      override def reset(): Unit = head.stageShutdown()     // shutdown the stage after a complete request
    }
    pipeline.LeafBuilder(httpStage).base(head)
    head.sendInboundCommand(Cmd.Connected)
    head.result
  }

  "Http4sStage" should {
    ServerTestRoutes.testRequestResults.zipWithIndex.foreach { case ((req, (status,headers,resp)), i) =>
      s"Run request $i Run request: --------\n${req.split("\r\n\r\n")(0)}\n" in {
        val result = runRequest(Seq(req))
        result.map(ResponseParser.apply(_)) must be_== ((status, headers, resp)).await
      }
    }
  }
}
