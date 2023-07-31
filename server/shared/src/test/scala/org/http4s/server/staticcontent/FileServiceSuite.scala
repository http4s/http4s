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
import cats.syntax.all._
import fs2._
import fs2.io.file._
import org.http4s.headers.Range.SubRange
import org.http4s.headers.`Content-Range`
import org.http4s.server.middleware.TranslateUri
import org.http4s.syntax.all._

class FileServiceSuite extends Http4sSuite with StaticContentShared {

  val routes: HttpRoutes[IO] = fileService(
    FileService.Config[IO](defaultSystemPath)
  )

  test("Respect UriTranslation") {
    val app = TranslateUri("/foo")(routes).orNotFound

    testResource.flatMap { testResource =>
      val req = Request[IO](uri = uri"/foo/testresource.txt")
      Stream.eval(app(req)).flatMap(_.body.chunks).compile.lastOrError.assertEquals(testResource) *>
        app(req).map(_.status).assertEquals(Status.Ok)
    } *> {
      val req = Request[IO](uri = uri"/testresource.txt")
      app(req).map(_.status).assertEquals(Status.NotFound)
    }
  }

  test("Return a 200 Ok file") {
    val req = Request[IO](uri = uri"/testresource.txt")
    testResource.flatMap { testResource =>
      Stream
        .eval(routes.orNotFound.run(req))
        .flatMap(_.body.chunks)
        .compile
        .lastOrError
        .assertEquals(testResource) *>
        routes.orNotFound(req).map(_.status).assertEquals(Status.Ok)
    }
  }

  test("Return a 404 for a resource under an existing file") {
    val req = Request[IO](uri = uri"/testresource.txt/test")
    routes.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Decodes path segments") {
    val req = Request[IO](uri = uri"/space+truckin%27.txt")
    routes.orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Respect the path prefix") {
    val relativePath = "testresource.txt"
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = defaultSystemPath,
        pathPrefix = "/path-prefix",
      )
    )
    val file = Path(defaultSystemPath).resolve(relativePath)
    val uri = Uri.unsafeFromString("/path-prefix/" + relativePath)
    val req = Request[IO](uri = uri)
    Files[IO].exists(file).assert *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Return a 400 if the request tries to escape the context") {
    val relativePath = "../testresource.txt"
    val systemPath = Path(defaultSystemPath).resolve("testDir")
    val file = systemPath.resolve(relativePath)

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = systemPath.toString
      )
    )
    Files[IO].exists(file).assert *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Return a 400 on path traversal, even if it's inside the context") {
    val relativePath = "testDir/../testresource.txt"
    val file = Path(defaultSystemPath).resolve(relativePath)

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    Files[IO].exists(file).assert *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test(
    "Return a 404 Not Found if the request tries to escape the context with a partial system path prefix match"
  ) {
    val relativePath = "Dir/partial-prefix.txt"
    val file = Path(defaultSystemPath).resolve(relativePath)

    val uri = Uri.unsafeFromString("/test" + relativePath)
    val req = Request[IO](uri = uri)
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = Path(defaultSystemPath).resolve("test").toString
      )
    )
    Files[IO].exists(file).assert *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test(
    "Return a 404 Not Found if the request tries to escape the context with a partial path-prefix match"
  ) {
    val relativePath = "Dir/partial-prefix.txt"
    val file = Path(defaultSystemPath).resolve(relativePath)

    val uri = Uri.unsafeFromString("/prefix" + relativePath)
    val req = Request[IO](uri = uri)
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = defaultSystemPath,
        pathPrefix = "/prefix",
      )
    )
    Files[IO].exists(file).assert *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Return a 400 if the request tries to escape the context with /") {
    val absPath = Path(defaultSystemPath).resolve("testresource.txt")
    val file = absPath

    val uri = Uri.unsafeFromString("///" + absPath)
    val req = Request[IO](uri = uri)
    Files[IO].exists(file).assert *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("return files included via symlink") {
    val relativePath = "symlink/org/http4s/server/RouterSuite.scala"
    val path = Path(defaultSystemPath).resolve(relativePath)
    val file = path
    Files[IO].readAll(path).chunks.compile.foldMonoid.flatMap { bytes =>
      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      Files[IO].exists(file).assert *>
        Files[IO].isSymbolicLink(Path(defaultSystemPath).resolve("symlink")).assert *>
        routes.orNotFound(req).map(_.status).assertEquals(Status.Ok) *>
        Stream
          .eval(routes.orNotFound(req))
          .flatMap(_.body.chunks)
          .compile
          .lastOrError
          .assertEquals(bytes)
    }
  }

  test("Return index.html if request points to ''") {
    val path = Path(defaultSystemPath).resolve("testDir/").absolute.toString
    val s0 = fileService(FileService.Config[IO](systemPath = path))
    val req = Request[IO](uri = uri"")
    s0.orNotFound(req)
      .flatMap { res =>
        res.as[String].map {
          _ === "<html>Hello!</html>" && res.status === Status.Ok
        }
      }
      .assert
  }

  test("Return index.html if request points to '/'") {
    val path = Path(defaultSystemPath).resolve("testDir/").absolute.toString
    val s0 = fileService(FileService.Config[IO](systemPath = path))
    val req = Request[IO](uri = uri"/")
    val rb = s0.orNotFound(req)

    rb.flatMap { res =>
      res.as[String].map(_ === "<html>Hello!</html>" && res.status === Status.Ok)
    }.assert
  }

  test("Return index.html if request points to a directory") {
    val req = Request[IO](uri = uri"/testDir/")
    val rb = runReq(req)

    rb.flatMap { case (_, re) =>
      re.as[String]
        .map(_ === "<html>Hello!</html>" && re.status === Status.Ok)
    }.assert
  }

  test("Not find missing file") {
    val req = Request[IO](uri = uri"/missing.txt")
    routes.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Return a 206 PartialContent file") {
    val range = headers.Range(4)
    val req = Request[IO](uri = uri"/testresource.txt").withHeaders(range)
    testResource.flatMap { testResource =>
      Stream
        .eval(routes.orNotFound(req))
        .flatMap(_.body.chunks)
        .compile
        .lastOrError
        .assertEquals(Chunk.array(testResource.toArray.splitAt(4)._2)) *>
        routes.orNotFound(req).map(_.status).assertEquals(Status.PartialContent)
    }
  }

  test("Return a 206 PartialContent file") {
    val range = headers.Range(-4)
    val req = Request[IO](uri = uri"/testresource.txt").withHeaders(range)
    testResource.flatMap { testResource =>
      Stream
        .eval(routes.orNotFound(req))
        .flatMap(_.body.chunks)
        .compile
        .lastOrError
        .assertEquals(Chunk.array(testResource.toArray.splitAt(testResource.size - 4)._2)) *>
        routes.orNotFound(req).map(_.status).assertEquals(Status.PartialContent)
    }
  }

  test("Return a 206 PartialContent file") {
    val range = headers.Range(2, 4)
    val req = Request[IO](uri = uri"/testresource.txt").withHeaders(range)
    testResource.flatMap { testResource =>
      Stream
        .eval(routes.orNotFound(req))
        .flatMap(_.body.chunks)
        .compile
        .lastOrError
        .assertEquals(Chunk.array(testResource.toArray.slice(2, 4 + 1))) *>
        routes.orNotFound(req).map(_.status).assertEquals(Status.PartialContent)
      // the end number is inclusive in the Range header
    }
  }

  test("Return a 416 RangeNotSatisfiable on invalid range") {
    val ranges = List(
      headers.Range(2, -1),
      headers.Range(2, 1),
      headers.Range(200),
      headers.Range(200, 201),
      headers.Range(-200),
    )
    Files[IO].size(Path(defaultSystemPath) / "testresource.txt").flatMap { size =>
      val reqs = ranges.map(r => Request[IO](uri = uri"/testresource.txt").withHeaders(r))
      reqs.parTraverse_ { req =>
        routes.orNotFound(req).map(_.status).assertEquals(Status.RangeNotSatisfiable) *>
          routes
            .orNotFound(req)
            .map(_.headers.get[`Content-Range`])
            .assertEquals(Some(headers.`Content-Range`(SubRange(0, size - 1), Some(size))))
      }
    }
  }

  test("doesn't crash on /") {
    routes.orNotFound(Request[IO](uri = uri"/")).map(_.status).assertEquals(Status.NotFound)
  }

  test("handle a relative system path") {
    val s = fileService(FileService.Config[IO]("."))
    Files[IO].exists(Path(".").resolve("build.sbt")).assert *>
      s.orNotFound(Request[IO](uri = uri"/build.sbt")).map(_.status).assertEquals(Status.Ok)
  }

  test("404 if system path is not found") {
    val s = fileService(FileService.Config[IO]("./does-not-exist"))
    s.orNotFound(Request[IO](uri = uri"/build.sbt")).map(_.status).assertEquals(Status.NotFound)
  }
}
