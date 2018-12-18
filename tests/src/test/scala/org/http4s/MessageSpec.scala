package org.http4s

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Pure
import java.net.{InetAddress, InetSocketAddress}
import org.http4s.headers.{Authorization, `Content-Type`, `X-Forwarded-For`}
import _root_.io.chrisdavenport.vault._

class MessageSpec extends Http4sSpec {

  "Request" >> {
    "ConnectionInfo" should {
      val local = InetSocketAddress.createUnresolved("www.local.com", 8080)
      val remote = InetSocketAddress.createUnresolved("www.remote.com", 45444)

      "get remote connection info when present" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.server must beSome(local)
        r.remote must beSome(remote)
      }

      "not contain remote connection info when not present" in {
        val r = Request()
        r.server must beNone
        r.remote must beNone
      }

      "be utilized to determine the address of server and remote" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.serverAddr must_== local.getHostString
        r.remoteAddr must beSome(remote.getHostString)
      }

      "be utilized to determine the port of server and remote" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.serverPort must_== local.getPort
        r.remotePort must beSome(remote.getPort)
      }

      "be utilized to determine the from value (first X-Forwarded-For if present)" in {
        val forwardedValues =
          NonEmptyList.of(Some(InetAddress.getLocalHost), Some(InetAddress.getLoopbackAddress))
        val r = Request()
          .withHeaders(Headers(`X-Forwarded-For`(forwardedValues)))
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.from must_== forwardedValues.head
      }

      "be utilized to determine the from value (remote value if X-Forwarded-For is not present)" in {
        val r = Request()
          .withAttribute(Request.Keys.ConnectionInfo, Request.Connection(local, remote, false))
        r.from must_== Option(remote.getAddress)
      }

    }

    "support cookies" should {
      "contain a Cookie header when an explicit cookie is added" in {
        Request(Method.GET)
          .addCookie(RequestCookie("token", "value"))
          .headers
          .get("Cookie".ci)
          .map(_.value) must beSome("token=value")
      }

      "contain a Cookie header when a name/value pair is added" in {
        Request(Method.GET)
          .addCookie("token", "value")
          .headers
          .get("Cookie".ci)
          .map(_.value) must beSome("token=value")
      }
    }

    "Request.with..." should {
      val path1 = "/path1"
      val path2 = "/somethingelse"
      val attributes = Vault.empty.insert(Request.Keys.PathInfoCaret, 3)

      "reset pathInfo if uri is changed" in {
        val originalReq = Request(uri = Uri(path = path1), attributes = attributes)
        val updatedReq = originalReq.withUri(uri = Uri(path = path2))

        updatedReq.scriptName mustEqual ""
        updatedReq.pathInfo mustEqual path2
      }

      "not modify pathInfo if uri is unchanged" in {
        val originalReq = Request(uri = Uri(path = path1), attributes = attributes)
        val updatedReq = originalReq.withMethod(method = Method.DELETE)

        originalReq.pathInfo mustEqual updatedReq.pathInfo
        originalReq.scriptName mustEqual updatedReq.scriptName
      }
    }

    "toString" should {
      "redact an Authorization header" in {
        val request =
          Request[IO](Method.GET).putHeaders(Authorization(BasicCredentials("user", "pass")))
        request.toString must_== ("Request(method=GET, uri=/, headers=Headers(Authorization: <REDACTED>))")
      }

      "redact Cookie Headers" in {
        val request =
          Request[IO](Method.GET).addCookie("token", "value").addCookie("token2", "value2")
        request.toString must_== ("Request(method=GET, uri=/, headers=Headers(Cookie: <REDACTED>, Cookie: <REDACTED>))")
      }
    }
  }

  "Message" >> {
    "decode" should {
      "produce a UnsupportedMediaType in the event of a decode failure" >> {
        "MediaTypeMismatch" in {
          val req =
            Request[IO](headers = Headers(`Content-Type`(MediaType.application.`octet-stream`)))
          val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
          resp.map(_.status) must returnValue(Status.UnsupportedMediaType)
        }
        "MediaTypeMissing" in {
          val req = Request[IO]()
          val resp = req.decodeWith(EntityDecoder.text, strict = true)(_ => IO.pure(Response()))
          resp.map(_.status) must returnValue(Status.UnsupportedMediaType)
        }
      }
    }
  }

  "Response" >> {
    "toString" should {
      "redact a `Set-Cookie` header" in {
        val resp = Response().putHeaders(headers.`Set-Cookie`(ResponseCookie("token", "value")))
        resp.toString must_== ("Response(status=200, headers=Headers(Set-Cookie: <REDACTED>))")
      }
    }

    "notFound" should {
      "return a plain text UTF-8 not found response" in {
        val resp: Response[Pure] = Response.notFound

        resp.contentType must beSome(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`))
        resp.status must_=== Status.NotFound
        resp.body.through(fs2.text.utf8Decode).toList.mkString("") must_=== "Not found"
      }
    }
  }

}
