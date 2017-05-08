package org.http4s
package server
package middleware

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

import cats.implicits._
import org.http4s.server.syntax._
import org.http4s.dsl._
import org.http4s.headers._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

class GZipSpec extends Http4sSpec {
  "GZip" should {
    "fall through if the route doesn't match" in {
      val service = GZip(HttpService.empty) |+| HttpService {
        case GET -> Root =>
          Ok("pong")
      }
      val req = Request(Method.GET, Uri.uri("/"))
        .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val resp = service.orNotFound(req).unsafeRun
      resp.status must_== (Status.Ok)
      resp.headers.get(`Content-Encoding`) must beNone
    }

    checkAll("encoding", new Properties("GZip") {
      property("middleware encoding == GZIPOutputStream encoding") = forAll { value: String =>
        val service = GZip(HttpService { case GET -> Root => Ok(value) })
        val req = Request(Method.GET, Uri.uri("/")).putHeaders(`Accept-Encoding`(ContentCoding.gzip))
        val actual = service.orNotFound(req).unsafeRun.body.runLog.unsafeRun.toArray

        val byteArrayStream = new ByteArrayOutputStream()
        val gzipStream = new GZIPOutputStream(byteArrayStream)
        gzipStream.write(value.getBytes)
        gzipStream.close()
        val expected = byteArrayStream.toByteArray

        actual === expected
      }
    })
  }
}
