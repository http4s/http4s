/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.effect.IO
import java.nio.file.Paths
import org.http4s.Uri.uri
import org.http4s.headers.{`Accept-Encoding`, `If-Modified-Since`}
import org.http4s.server.middleware.TranslateUri
import org.http4s.testing.Http4sLegacyMatchersIO

class ResourceServiceSpec extends Http4sSpec with StaticContentShared with Http4sLegacyMatchersIO {
  val config =
    ResourceService.Config[IO]("", blocker = testBlocker)
  val defaultBase = getClass.getResource("/").getPath.toString
  val routes = resourceService(config)

  "ResourceService" should {
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

    "Serve available content" in {
      val req = Request[IO](uri = Uri.fromString("/testresource.txt").yolo)
      val rb = routes.orNotFound(req)

      rb must returnBody(testResource)
      rb must returnStatus(Status.Ok)
    }

    "Decodes path segments" in {
      val req = Request[IO](uri = uri("/space+truckin%27.txt"))
      routes.orNotFound(req) must returnStatus(Status.Ok)
    }

    "Respect the path prefix" in {
      val relativePath = "testresource.txt"
      val s0 = resourceService(
        ResourceService.Config[IO](
          basePath = "",
          blocker = testBlocker,
          pathPrefix = "/path-prefix"
        ))
      val file = Paths.get(defaultBase).resolve(relativePath).toFile
      file.exists() must beTrue
      val uri = Uri.unsafeFromString("/path-prefix/" + relativePath)
      val req = Request[IO](uri = uri)
      s0.orNotFound(req) must returnStatus(Status.Ok)
    }

    "Return a 400 if the request tries to escape the context" in {
      val relativePath = "../testresource.txt"
      val basePath = Paths.get(defaultBase).resolve("testDir")
      val file = basePath.resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      val s0 = resourceService(
        ResourceService.Config[IO](
          basePath = "/testDir",
          blocker = testBlocker
        ))
      s0.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Return a 400 on path traversal, even if it's inside the context" in {
      val relativePath = "testDir/../testresource.txt"
      val file = Paths.get(defaultBase).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Return a 404 Not Found if the request tries to escape the context with a partial base path prefix match" in {
      val relativePath = "Dir/partial-prefix.txt"
      val file = Paths.get(defaultBase).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/test" + relativePath)
      val req = Request[IO](uri = uri)
      val s0 = resourceService(
        ResourceService.Config[IO](
          basePath = "",
          blocker = testBlocker
        ))
      s0.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Return a 404 Not Found if the request tries to escape the context with a partial path-prefix match" in {
      val relativePath = "Dir/partial-prefix.txt"
      val file = Paths.get(defaultBase).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/test" + relativePath)
      val req = Request[IO](uri = uri)
      val s0 = resourceService(
        ResourceService.Config[IO](
          basePath = "",
          blocker = testBlocker,
          pathPrefix = "/test"
        ))
      s0.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Return a 400 Not Found if the request tries to escape the context with /" in {
      val absPath = Paths.get(defaultBase).resolve("testresource.txt")
      val file = absPath.toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("///" + absPath)
      val req = Request[IO](uri = uri)
      val s0 = resourceService(
        ResourceService.Config[IO](
          basePath = "/testDir",
          blocker = testBlocker
        ))
      s0.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Try to serve pre-gzipped content if asked to" in {
      val req = Request[IO](
        uri = Uri.fromString("/testresource.txt").yolo,
        headers = Headers.of(`Accept-Encoding`(ContentCoding.gzip))
      )
      val rb = resourceService(config.copy(preferGzipped = true)).orNotFound(req)

      rb must returnBody(testResourceGzipped)
      rb must returnStatus(Status.Ok)
      rb must returnValue(haveMediaType(MediaType.text.plain))
      rb must returnValue(haveContentCoding(ContentCoding.gzip))
    }

    "Fallback to un-gzipped file if pre-gzipped version doesn't exist" in {
      val req = Request[IO](
        uri = Uri.fromString("/testresource2.txt").yolo,
        headers = Headers.of(`Accept-Encoding`(ContentCoding.gzip))
      )
      val rb = resourceService(config.copy(preferGzipped = true)).orNotFound(req)

      rb must returnBody(testResource)
      rb must returnStatus(Status.Ok)
      rb must returnValue(haveMediaType(MediaType.text.plain))
      rb must not(returnValue(haveContentCoding(ContentCoding.gzip)))
    }

    "Generate non on missing content" in {
      val req = Request[IO](uri = Uri.fromString("/testresource.txtt").yolo)
      routes.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Not send unmodified files" in {
      val req = Request[IO](uri = uri("/testresource.txt"))
        .putHeaders(`If-Modified-Since`(HttpDate.MaxValue))

      runReq(req)._2.status must_== Status.NotModified
    }

    "doesn't crash on /" in {
      routes.orNotFound(Request[IO](uri = uri("/"))) must returnStatus(Status.NotFound)
    }
  }
}
