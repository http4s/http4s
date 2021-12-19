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
import fs2.io.file.{Files, Path}
import org.http4s.syntax.all._

private[staticcontent] trait StaticContentShared { this: Http4sSuite =>
  def routes: HttpRoutes[IO]

  protected val defaultSystemPath: String =
    org.http4s.server.test.BuildInfo.test_resourceDirectory
      .replace("jvm", "shared")
      .replace("js", "shared")

  lazy val testResource: IO[Chunk[Byte]] =
    Files[IO].readAll(Path(defaultSystemPath) / "testresource.txt").chunks.compile.foldMonoid

  lazy val testResourceGzipped: IO[Chunk[Byte]] =
    Files[IO].readAll(Path(defaultSystemPath) / "testresource.txt.gz").chunks.compile.foldMonoid

  def runReq(
      req: Request[IO],
      routes: HttpRoutes[IO] = routes,
  ): IO[(IO[Chunk[Byte]], Response[IO])] =
    routes.orNotFound(req).map { resp =>
      (resp.body.compile.to(Array).map(Chunk.array[Byte]), resp)
    }

}
