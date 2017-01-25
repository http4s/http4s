package org.http4s
package server
package staticcontent

import java.nio.charset.StandardCharsets

import fs2._

private [staticcontent] trait StaticContentShared { this: Http4sSpec =>

  def s: HttpService

  lazy val testResource = {
    val s = getClass.getResourceAsStream("/testresource.txt")
    require(s != null, "Couldn't acquire resource!")
    val bytes = scala.io.Source.fromInputStream(s)
      .mkString
      .getBytes(StandardCharsets.UTF_8)

    Chunk.bytes(bytes)
  }
}
