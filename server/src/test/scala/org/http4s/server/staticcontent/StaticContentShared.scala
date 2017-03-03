package org.http4s
package server
package staticcontent

import java.nio.charset.StandardCharsets

import scodec.bits.ByteVector


private [staticcontent] trait StaticContentShared { this: Http4sSpec =>

  def s: HttpService

  lazy val testResource = {
    val s = getClass.getResourceAsStream("/testresource.txt")
    require(s != null, "Couldn't acquire resource!")
    val bytes = scala.io.Source.fromInputStream(s)
      .mkString
      .getBytes(StandardCharsets.UTF_8)

    ByteVector.view(bytes)
  }

  lazy val testWebjarResource: ByteVector = {
    val s = getClass.getResourceAsStream("/META-INF/resources/webjars/test-lib/1.0.0/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    ByteVector.view(
      scala.io.Source.fromInputStream(s)
        .mkString
        .getBytes(StandardCharsets.UTF_8)
    )
  }

  lazy val testWebjarSubResource: ByteVector = {
    val s = getClass.getResourceAsStream("/META-INF/resources/webjars/test-lib/1.0.0/sub/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    ByteVector.view(
      scala.io.Source.fromInputStream(s)
        .mkString
        .getBytes(StandardCharsets.UTF_8)
    )
  }

  def runReq(req: Request): (ByteVector, Response) = {
    val resp = s.orNotFound(req).unsafePerformSync
    val body = resp.body.runLog.unsafePerformSync.fold(ByteVector.empty)(_ ++ _)
    (body, resp)
  }

}
