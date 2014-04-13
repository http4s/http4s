package org.http4s.blaze

/**
 * Created by Bryce Anderson on 3/28/14.
 */

import org.scalatest.{Matchers, WordSpec}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.http4s.blaze.pipeline.{Command => Cmd}
import scala.concurrent.Await
import scala.concurrent.duration._


class Http4sStageSpec extends WordSpec with Matchers {

  def runRequest(req: Seq[String]): ByteBuffer = {
    val head = new SeqTestHead(req.map(s => ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII))))
    pipeline.LeafBuilder(new Http1Stage(TestRoutes())) .base(head)
    head.sendInboundCommand(Cmd.Connect)
    if (!head.result.isCompleted) {
      Thread.sleep(300)
      head.stageShutdown()
    }
    Await.result(head.result, 100.milliseconds)
  }

  "Http4sStage" should {

    TestRoutes.testRequestResults.zipWithIndex.foreach { case ((req, (status,headers,resp)), i) =>
      s"Run request $i Run request: --------\n${req.split("\r\n\r\n")(0)}\n" in {

        val result = runRequest(Seq(req))

        val (sresult,hresult,body) = ResponseParser(result)

        status should equal(sresult)
        body should equal(resp)
        headers should equal(hresult)

      }
    }
  }

}
