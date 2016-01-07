package org.http4s
package server
package staticcontent

import java.nio.charset.StandardCharsets

import scodec.bits.ByteVector


private [staticcontent] trait StaticContentShared {

  def s: HttpService

  lazy val testResource = {
    val s = getClass.getResourceAsStream("/testresource.txt")
    require(s != null, "Couldn't acquire resource!")
    val bytes = scala.io.Source.fromInputStream(s)
      .mkString
      .getBytes(StandardCharsets.UTF_8)

    ByteVector.view(bytes)
  }

  def runReq(req: Request): (ByteVector, Response) = {
    val resp = s(req).run
    val body = resp.body.runLog.run.fold(ByteVector.empty)(_ ++ _)
    (body, resp)
  }
}
