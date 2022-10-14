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
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.syntax.all._
import org.http4s.testing.AutoCloseableResource

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

private[staticcontent] trait StaticContentShared { this: Http4sSuite =>
  def routes: HttpRoutes[IO]

  protected val defaultSystemPath: String =
    org.http4s.server.test.BuildInfo.test_resourceDirectory
      .replace("jvm", "shared")
      .replace("js", "shared")
      .replace("native", "shared")

  lazy val testResource: IO[Chunk[Byte]] =
    Files[IO].readAll(Path(defaultSystemPath) / "testresource.txt").chunks.compile.foldMonoid

  lazy val testResourceGzipped: IO[Chunk[Byte]] =
    Files[IO].readAll(Path(defaultSystemPath) / "testresource.txt.gz").chunks.compile.foldMonoid

  lazy val testWebjarResource: Chunk[Byte] = {
    val s =
      getClass.getResourceAsStream("/META-INF/resources/webjars/test-lib/1.0.0/testresource.txt")
    require(s != null, "Couldn't acquire resource!")

    Chunk.array(
      AutoCloseableResource.resource(
        scala.io.Source
          .fromInputStream(s)
      )(
        _.mkString
          .getBytes(StandardCharsets.UTF_8)
      )
    )
  }

  lazy val testWebjarResourceGzipped: Chunk[Byte] = {
    val url =
      getClass.getResource("/META-INF/resources/webjars/test-lib/1.0.0/testresource.txt.gz")
    require(url != null, "Couldn't acquire resource!")

    val bytes = java.nio.file.Files.readAllBytes(Paths.get(url.toURI))
    Chunk.array(bytes)
  }

  lazy val testWebjarSubResource: Chunk[Byte] = {
    val s = getClass.getResourceAsStream(
      "/META-INF/resources/webjars/test-lib/1.0.0/sub/testresource.txt"
    )
    require(s != null, "Couldn't acquire resource!")

    Chunk.array(
      AutoCloseableResource.resource(
        scala.io.Source
          .fromInputStream(s)
      )(
        _.mkString
          .getBytes(StandardCharsets.UTF_8)
      )
    )
  }

  def runReq(
      req: Request[IO],
      routes: HttpRoutes[IO] = routes,
  ): IO[(IO[Chunk[Byte]], Response[IO])] =
    routes.orNotFound(req).map { resp =>
      (resp.body.compile.to(Array).map(Chunk.array[Byte]), resp)
    }

}
