package org.http4s
package server
package staticcontent

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import fs2._
import scodec.bits.ByteVector
import org.http4s.util.ByteVectorChunk

private[staticcontent] trait StaticContentShared { this: Http4sSpec =>

  def s: HttpService

  lazy val testResource: Chunk[Byte] = {
    val s = getClass.getResourceAsStream("/testresource.txt")
    require(s != null, "Couldn't acquire resource!")
    val bytes = scala.io.Source.fromInputStream(s)
      .mkString
      .getBytes(StandardCharsets.UTF_8)

    ByteVectorChunk(ByteVector.view(bytes))
  }

  lazy val testResourceGzipped: Chunk[Byte] = {
    val url = getClass.getResource("/testresource.txt.gz")
    require(url != null, "Couldn't acquire resource!")
    val bytes = Files.readAllBytes(Paths.get(url.toURI))

    ByteVectorChunk(ByteVector.view(bytes))
  }

  lazy val testWebjarResource: ByteVector = {
    val s = getClass.getResourceAsStream("/META-INF/resources/webjars/test-lib/1.0.0/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    ByteVector.view(
      scala.io.Source.fromInputStream(s)
        .mkString
        .getBytes(StandardCharsets.UTF_8))
  }

  lazy val testWebjarSubResource: ByteVector = {
    val s = getClass.getResourceAsStream("/META-INF/resources/webjars/test-lib/1.0.0/sub/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    ByteVector.view(
      scala.io.Source.fromInputStream(s)
        .mkString
        .getBytes(StandardCharsets.UTF_8))
  }

  def runReq(req: Request): (ByteVector, Response) = {
    val resp = s.orNotFound(req).unsafeRun
    val body = resp.body.chunks.runLog.unsafeRun.foldLeft(ByteVector.empty) {
      (bv, chunk) => bv ++ ByteVector.view(chunk.toArray)
    }
    (body, resp)
  }
}
