package org.http4s

import java.net.InetSocketAddress

import fs2._
import org.http4s.headers.`Content-Type`

class MessageSpec extends Http4sSpec {

  "Request" should {
    "ConnectionInfo" should {
      val local = InetSocketAddress.createUnresolved("www.local.com", 8080)
      val remote = InetSocketAddress.createUnresolved("www.remote.com", 45444)

      "get remote connection info when present" in {
        val r = Request().withAttribute(Request.Keys.ConnectionInfo(Request.Connection(local, remote, false)))
        r.server must beSome(local)
        r.remote must beSome(remote)
      }

      "not contain remote connection info when not present" in {
        val r = Request()
        r.server must beNone
        r.remote must beNone
      }

      "be utilized to determine the address of server and remote" in {
        val r = Request().withAttribute(Request.Keys.ConnectionInfo(Request.Connection(local, remote, false)))
        r.serverAddr must_== local.getHostString
        r.remoteAddr must beSome(remote.getHostString)
      }

      "be utilized to determine the port of server and remote" in {
        val r = Request().withAttribute(Request.Keys.ConnectionInfo(Request.Connection(local, remote, false)))
        r.serverPort must_== local.getPort
        r.remotePort must beSome(remote.getPort)
      }
    }

    /* TODO fs2 spec bring back when unemit comes back
    "isIdempotent" should {
      "be true if the method is idempotent and the body is pure" in {
        Request(Method.GET).withBody("pure").map(_.isIdempotent) must returnValue(true)
      }

      "be false if the body is effectful" in {
        Request(Method.GET).withBody(Task.now("effectful")).map(_.isIdempotent) must returnValue(true)
      }

      "be false if the method is not idempotent" in {
        Request(Method.POST).isIdempotent must beFalse
      }
    }
     */

    "support cookies" should {
      "contain a Cookie header when an explicit cookie is added" in {
        Request(Method.GET).addCookie(Cookie("token", "value")).headers.get("Cookie".ci).map(_.value) must beSome("token=value")
      }

      "contain a Cookie header when a name/value pair is added" in {
        Request(Method.GET).addCookie("token", "value").headers.get("Cookie".ci).map(_.value) must beSome("token=value")
      }
    }
  }

  "Message" should {
    "decode" >> {
      "produce a UnsupportedMediaType in the event of a decode failure" >> {
        "MediaTypeMismatch" in {
          val req = Request(headers = Headers(`Content-Type`(MediaType.`application/base64`)))
          val resp = req.decodeWith(EntityDecoder.text, strict = true)(txt => Task.now(Response()))
          resp.map(_.status) must returnValue(Status.UnsupportedMediaType)
        }
        "MediaTypeMissing" in {
          val req = Request()
          val resp = req.decodeWith(EntityDecoder.text, strict = true)(txt => Task.now(Response()))
          resp.map(_.status) must returnValue(Status.UnsupportedMediaType)
        }
      }
    }
  }

}
