package org.http4s
package server
package staticcontent

import cats.effect._
import fs2._
import java.io.File
import java.nio.file._
import org.http4s.Uri.uri
import org.http4s.server.middleware.TranslateUri
import org.http4s.headers.Range.SubRange

class FileServiceSpec extends Http4sSpec with StaticContentShared {
  val defaultSystemPath = test.BuildInfo.test_resourceDirectory.getAbsolutePath
  val routes = fileService(FileService.Config[IO](defaultSystemPath))

  "FileService" should {

    "Respect UriTranslation" in {
      val app = TranslateUri("/foo")(routes).orNotFound

      {
        val req = Request[IO](uri = uri("/foo/testresource.txt"))
        app(req) must returnBody(testResource)
        app(req) must returnStatus(Status.Ok)
      }

      {
        val req = Request[IO](uri = uri("/testresource.txt"))
        app(req) must returnStatus(Status.NotFound)
      }
    }

    "Return a 200 Ok file" in {
      val req = Request[IO](uri = uri("/testresource.txt"))
      routes.orNotFound(req) must returnBody(testResource)
      routes.orNotFound(req) must returnStatus(Status.Ok)
    }

    "Decodes path segments" in {
      val req = Request[IO](uri = uri("/space+truckin%27.txt"))
      routes.orNotFound(req) must returnStatus(Status.Ok)
    }

    "Respect the path prefix" in {
      val relativePath = "testresource.txt"
      val s0 = fileService(
        FileService.Config[IO](
          systemPath = defaultSystemPath,
          pathPrefix = "/path-prefix"
        ))
      val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile
      file.exists() must beTrue
      val uri = Uri.unsafeFromString("/path-prefix/" + relativePath)
      val req = Request[IO](uri = uri)
      s0.orNotFound(req) must returnStatus(Status.Ok)
    }

    "Return a 400 if the request tries to escape the context" in {
      val relativePath = "../testresource.txt"
      val systemPath = Paths.get(defaultSystemPath).resolve("testDir")
      val file = systemPath.resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      val s0 = fileService(
        FileService.Config[IO](
          systemPath = systemPath.toString
        ))
      s0.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Return a 400 on path traversal, even if it's inside the context" in {
      val relativePath = "testDir/../testresource.txt"
      val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Return a 404 Not Found if the request tries to escape the context with a partial system path prefix match" in {
      val relativePath = "Dir/partial-prefix.txt"
      val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/test" + relativePath)
      val req = Request[IO](uri = uri)
      val s0 = fileService(
        FileService.Config[IO](
          systemPath = Paths.get(defaultSystemPath).resolve("test").toString
        ))
      s0.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Return a 404 Not Found if the request tries to escape the context with a partial path-prefix match" in {
      val relativePath = "Dir/partial-prefix.txt"
      val file = Paths.get(defaultSystemPath).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/prefix" + relativePath)
      val req = Request[IO](uri = uri)
      val s0 = fileService(
        FileService.Config[IO](
          systemPath = defaultSystemPath,
          pathPrefix = "/prefix"
        ))
      s0.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Return a 400 if the request tries to escape the context with /" in {
      val absPath = Paths.get(defaultSystemPath).resolve("testresource.txt")
      val file = absPath.toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("///" + absPath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "return files included via symlink" in {
      val relativePath = "symlink/org/http4s/server/staticcontent/FileServiceSpec.scala"
      val path = Paths.get(defaultSystemPath).resolve(relativePath)
      val file = path.toFile
      Files.isSymbolicLink(Paths.get(defaultSystemPath).resolve("symlink")) must beTrue
      file.exists() must beTrue
      val bytes = Chunk.bytes(Files.readAllBytes(path))

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.Ok)
      routes.orNotFound(req) must returnBody(bytes)
    }

    "Return index.html if request points to a directory" in {
      val req = Request[IO](uri = uri("/testDir/"))
      val rb = runReq(req)

      rb._2.as[String] must returnValue("<html>Hello!</html>")
      rb._2.status must_== Status.Ok
    }

    "Not find missing file" in {
      val req = Request[IO](uri = uri("/missing.txt"))
      routes.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(4)
      val req = Request[IO](uri = uri("/testresource.txt")).withHeaders(range)
      routes.orNotFound(req) must returnStatus(Status.PartialContent)
      routes.orNotFound(req) must returnBody(Chunk.bytes(testResource.toArray.splitAt(4)._2))
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(-4)
      val req = Request[IO](uri = uri("/testresource.txt")).withHeaders(range)
      routes.orNotFound(req) must returnStatus(Status.PartialContent)
      routes.orNotFound(req) must returnBody(
        Chunk.bytes(testResource.toArray.splitAt(testResource.size - 4)._2))
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(2, 4)
      val req = Request[IO](uri = uri("/testresource.txt")).withHeaders(range)
      routes.orNotFound(req) must returnStatus(Status.PartialContent)
      routes.orNotFound(req) must returnBody(Chunk.bytes(testResource.toArray.slice(2, 4 + 1))) // the end number is inclusive in the Range header
    }

    "Return a 416 RangeNotSatisfiable on invalid range" in {
      val ranges = Seq(
        headers.Range(2, -1),
        headers.Range(2, 1),
        headers.Range(200),
        headers.Range(200, 201),
        headers.Range(-200)
      )
      val size = new File(getClass.getResource("/testresource.txt").toURI).length
      val reqs = ranges.map(r => Request[IO](uri = uri("/testresource.txt")).withHeaders(r))
      forall(reqs) { req =>
        routes.orNotFound(req) must returnStatus(Status.RangeNotSatisfiable)
        routes.orNotFound(req) must returnValue(
          containsHeader(headers.`Content-Range`(SubRange(0, size - 1), Some(size))))
      }
    }

    "doesn't crash on /" in {
      routes.orNotFound(Request[IO](uri = uri("/"))) must returnStatus(Status.NotFound)
    }

    "handle a relative system path" in {
      val s = fileService(FileService.Config[IO]("."))
      Paths.get(".").resolve("build.sbt").toFile.exists() must beTrue
      s.orNotFound(Request[IO](uri = uri("/build.sbt"))) must returnStatus(Status.Ok)
    }

    "404 if system path is not found" in {
      val s = fileService(FileService.Config[IO]("./does-not-exist"))
      s.orNotFound(Request[IO](uri = uri("/build.sbt"))) must returnStatus(Status.NotFound)
    }
  }
}
