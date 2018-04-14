package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.http4s.dsl.io._
import org.http4s.headers._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

class GZipSpec extends Http4sSpec {

  "GZip" should {
    "fall through if the route doesn't match" in {
      val routes = GZip(HttpRoutes.empty[IO]) <+> HttpRoutes.of[IO] {
        case GET -> Root => Ok("pong")
      }
      val req =
        Request[IO](Method.GET, Uri.uri("/")).putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val resp = routes.orNotFound(req).unsafeRunSync()
      resp.status must_== (Status.Ok)
      resp.headers.get(`Content-Encoding`) must beNone
    }

    "encodes random content-type if given isZippable is true" in {
      val response = "Response string"
      val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case GET -> Root =>
          Ok(response, Header("Content-Type", "random-type; charset=utf-8"))
      }

      val gzipRoutes: HttpRoutes[IO] = GZip(routes, isZippable = (_) => true)

      val req: Request[IO] = Request[IO](Method.GET, Uri.uri("/"))
        .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val actual: IO[Array[Byte]] =
        gzipRoutes.orNotFound(req).flatMap(_.as[Chunk[Byte]]).map(_.toArray)

      val byteStream = new ByteArrayOutputStream(response.length)
      val gZIPStream = new GZIPOutputStream(byteStream)
      gZIPStream.write(response.getBytes)
      gZIPStream.close()

      actual must returnValue(byteStream.toByteArray)
    }

    checkAll(
      "encoding",
      new Properties("GZip") {
        property("middleware encoding == GZIPOutputStream encoding") = forAll {
          vector: Vector[Array[Byte]] =>
            val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
              case GET -> Root => Ok(Stream.emits(vector).covary[IO])
            }
            val gzipRoutes: HttpRoutes[IO] = GZip(routes)
            val req: Request[IO] = Request[IO](Method.GET, Uri.uri("/"))
              .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
            val actual: IO[Array[Byte]] =
              gzipRoutes.orNotFound(req).flatMap(_.as[Chunk[Byte]]).map(_.toArray)

            val byteArrayStream = new ByteArrayOutputStream()
            val gzipStream = new GZIPOutputStream(byteArrayStream)
            vector.foreach(gzipStream.write)
            gzipStream.close()
            val expected = byteArrayStream.toByteArray

            actual must returnValue(expected)
        }
      }
    )
  }
}
