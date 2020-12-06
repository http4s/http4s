/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.effect.IO
import cats.syntax.all._
import fs2._
import java.io.File
import java.nio.file._
import org.http4s.Uri.uri
import org.http4s.syntax.all._
import org.http4s.headers.Range.SubRange
import org.http4s.server.middleware.TranslateUri

class FileServiceSuite extends Http4sSuite with StaticContentShared {
  val defaultSystemPath = org.http4s.server.test.BuildInfo.test_resourceDirectory.getAbsolutePath
  val routes = fileService(
    FileService.Config[IO](new File(getClass.getResource("/").toURI).getPath, testBlocker))

  test("Respect UriTranslation") {
    val app = TranslateUri("/foo")(routes).orNotFound

    {
      val req = Request[IO](uri = uri("/foo/testresource.txt"))
      Stream.eval(app(req)).flatMap(_.body.chunks).compile.lastOrError.assertEquals(testResource) *>
        app(req).map(_.status).assertEquals(Status.Ok)
    } *> {
      val req = Request[IO](uri = uri("/testresource.txt"))
      app(req).map(_.status).assertEquals(Status.NotFound)
    }
  }

  test("Return a 200 Ok file") {
    val req = Request[IO](uri = uri("/testresource.txt"))
    Stream
      .eval(routes.orNotFound.run(req))
      .flatMap(_.body.chunks)
      .compile
      .lastOrError
      .assertEquals(testResource) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Decodes path segments") {
    val req = Request[IO](uri = uri("/space+truckin%27.txt"))
    routes.orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Respect the path prefix") {
    val relativePath = "testresource.txt"
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = defaultSystemPath,
        blocker = testBlocker,
        pathPrefix = "/path-prefix"
      ))
    val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile
    val uri = Uri.unsafeFromString("/path-prefix/" + relativePath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Return a 400 if the request tries to escape the context") {
    val relativePath = "../testresource.txt"
    val systemPath = Paths.get(defaultSystemPath).resolve("testDir")
    val file = systemPath.resolve(relativePath).toFile

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = systemPath.toString,
        blocker = testBlocker
      ))
    IO(file.exists()).assertEquals(true) *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("Return a 400 on path traversal, even if it's inside the context") {
    val relativePath = "testDir/../testresource.txt"
    val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test(
    "Return a 404 Not Found if the request tries to escape the context with a partial system path prefix match") {
    val relativePath = "Dir/partial-prefix.txt"
    val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile

    val uri = Uri.unsafeFromString("/test" + relativePath)
    val req = Request[IO](uri = uri)
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = Paths.get(defaultSystemPath).resolve("test").toString,
        blocker = testBlocker
      ))
    IO(file.exists()).assertEquals(true) *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test(
    "Return a 404 Not Found if the request tries to escape the context with a partial path-prefix match") {
    val relativePath = "Dir/partial-prefix.txt"
    val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile

    val uri = Uri.unsafeFromString("/prefix" + relativePath)
    val req = Request[IO](uri = uri)
    val s0 = fileService(
      FileService.Config[IO](
        systemPath = defaultSystemPath,
        pathPrefix = "/prefix",
        blocker = testBlocker
      ))
    IO(file.exists()).assertEquals(true) *>
      s0.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Return a 400 if the request tries to escape the context with /") {
    val absPath = Paths.get(defaultSystemPath).resolve("testresource.txt")
    val file = absPath.toFile

    val uri = Uri.unsafeFromString("///" + absPath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test("return files included via symlink") {
    val relativePath = "symlink/org/http4s/server/staticcontent/FileServiceSuite.scala"
    val path = Paths.get(defaultSystemPath).resolve(relativePath)
    val file = path.toFile
    val bytes = Chunk.bytes(Files.readAllBytes(path))

    val uri = Uri.unsafeFromString("/" + relativePath)
    val req = Request[IO](uri = uri)
    IO(file.exists()).assertEquals(true) *>
      IO(Files.isSymbolicLink(Paths.get(defaultSystemPath).resolve("symlink")))
        .assertEquals(true) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.Ok) *>
      Stream
        .eval(routes.orNotFound(req))
        .flatMap(_.body.chunks)
        .compile
        .lastOrError
        .assertEquals(bytes)
  }

  test("Return index.html if request points to ''") {
    val path = Paths.get(defaultSystemPath).resolve("testDir/").toAbsolutePath.toString
    val s0 = fileService(FileService.Config[IO](systemPath = path, blocker = testBlocker))
    val req = Request[IO](uri = uri(""))
    s0.orNotFound(req)
      .flatMap { res =>
        res.as[String].map {
          _ === "<html>Hello!</html>" && res.status === Status.Ok
        }
      }
      .assertEquals(true)
  }

  test("Return index.html if request points to '/'") {
    val path = Paths.get(defaultSystemPath).resolve("testDir/").toAbsolutePath.toString
    val s0 = fileService(FileService.Config[IO](systemPath = path, blocker = testBlocker))
    val req = Request[IO](uri = uri("/"))
    val rb = s0.orNotFound(req)

    rb.flatMap { res =>
      res.as[String].map(_ === "<html>Hello!</html>" && res.status === Status.Ok)
    }.assertEquals(true)
  }

  test("Return index.html if request points to a directory") {
    val req = Request[IO](uri = uri("/testDir/"))
    val rb = runReq(req)

    rb.flatMap { case (_, re) =>
      re.as[String]
        .map(_ === "<html>Hello!</html>" && re.status === Status.Ok)
    }.assertEquals(true)
  }

  test("Not find missing file") {
    val req = Request[IO](uri = uri("/missing.txt"))
    routes.orNotFound(req).map(_.status).assertEquals(Status.NotFound)
  }

  test("Return a 206 PartialContent file") {
    val range = headers.Range(4)
    val req = Request[IO](uri = uri("/testresource.txt")).withHeaders(range)
    Stream
      .eval(routes.orNotFound(req))
      .flatMap(_.body.chunks)
      .compile
      .lastOrError
      .assertEquals(Chunk.bytes(testResource.toArray.splitAt(4)._2)) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.PartialContent)
  }

  test("Return a 206 PartialContent file") {
    val range = headers.Range(-4)
    val req = Request[IO](uri = uri("/testresource.txt")).withHeaders(range)
    Stream
      .eval(routes.orNotFound(req))
      .flatMap(_.body.chunks)
      .compile
      .lastOrError
      .assertEquals(Chunk.bytes(testResource.toArray.splitAt(testResource.size - 4)._2)) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.PartialContent)
  }

  test("Return a 206 PartialContent file") {
    val range = headers.Range(2, 4)
    val req = Request[IO](uri = uri("/testresource.txt")).withHeaders(range)
    Stream
      .eval(routes.orNotFound(req))
      .flatMap(_.body.chunks)
      .compile
      .lastOrError
      .assertEquals(Chunk.bytes(testResource.toArray.slice(2, 4 + 1))) *>
      routes.orNotFound(req).map(_.status).assertEquals(Status.PartialContent)
    // the end number is inclusive in the Range header
  }

  test("Return a 416 RangeNotSatisfiable on invalid range") {
    val ranges = Seq(
      headers.Range(2, -1),
      headers.Range(2, 1),
      headers.Range(200),
      headers.Range(200, 201),
      headers.Range(-200)
    )
    val size = new File(getClass.getResource("/testresource.txt").toURI).length
    val reqs = ranges.map(r => Request[IO](uri = uri("/testresource.txt")).withHeaders(r))
    reqs.toList.traverse { req =>
      routes.orNotFound(req).map(_.status).assertEquals(Status.RangeNotSatisfiable) *>
        routes
          .orNotFound(req)
          .map(_.headers.toList.exists(
            _ === headers.`Content-Range`(SubRange(0, size - 1), Some(size))))
          .assertEquals(true)
    }
  }

  test("doesn't crash on /") {
    routes.orNotFound(Request[IO](uri = uri("/"))).map(_.status).assertEquals(Status.NotFound)
  }

  test("handle a relative system path") {
    val s = fileService(FileService.Config[IO](".", blocker = testBlocker))
    IO(Paths.get(".").resolve("build.sbt").toFile.exists()).assertEquals(true) *>
      s.orNotFound(Request[IO](uri = uri("/build.sbt"))).map(_.status).assertEquals(Status.Ok)
  }

  test("404 if system path is not found") {
    val s = fileService(FileService.Config[IO]("./does-not-exist", blocker = testBlocker))
    s.orNotFound(Request[IO](uri = uri("/build.sbt"))).map(_.status).assertEquals(Status.NotFound)
  }
}
