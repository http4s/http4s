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
    Await.result(head.result, 4.seconds)
  }


  "Http4sStage" should {
    "Run requests" in {

      TestRoutes.testRequestResults.foreach{ case (req, (status,headers,resp)) =>
        val result = runRequest(Seq(req))
        val (sresult,hresult,body) = ResponseParser(result)

        status should equal(sresult)
        body should equal(resp)
        headers should equal(hresult)

      }


    }
  }

}
