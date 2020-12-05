/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package staticcontent

import cats.effect.IO
import fs2._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

private[staticcontent] trait StaticContentShared { this: Http4sSpec =>
  def routes: HttpRoutes[IO]

  lazy val testResource: Chunk[Byte] = {
    val s = getClass.getResourceAsStream("/testresource.txt")
    require(s != null, "Couldn't acquire resource!")
    val bytes = scala.io.Source
      .fromInputStream(s)
      .mkString
      .getBytes(StandardCharsets.UTF_8)

    Chunk.bytes(bytes)
  }

  lazy val testResourceGzipped: Chunk[Byte] = {
    val url = getClass.getResource("/testresource.txt.gz")
    require(url != null, "Couldn't acquire resource!")
    val bytes = Files.readAllBytes(Paths.get(url.toURI))

    Chunk.bytes(bytes)
  }

  lazy val testWebjarResource: Chunk[Byte] = {
    val s =
      getClass.getResourceAsStream("/META-INF/resources/webjars/test-lib/1.0.0/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    Chunk.bytes(
      scala.io.Source
        .fromInputStream(s)
        .mkString
        .getBytes(StandardCharsets.UTF_8))
  }

  lazy val testWebjarSubResource: Chunk[Byte] = {
    val s = getClass.getResourceAsStream(
      "/META-INF/resources/webjars/test-lib/1.0.0/sub/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    Chunk.bytes(
      scala.io.Source
        .fromInputStream(s)
        .mkString
        .getBytes(StandardCharsets.UTF_8))
  }

  def runReq(req: Request[IO]): (Chunk[Byte], Response[IO]) = {
    val resp = routes.orNotFound(req).unsafeRunSync()
    val chunk = Chunk.bytes(resp.body.compile.to(Array).unsafeRunSync())
    (chunk, resp)
  }
}
