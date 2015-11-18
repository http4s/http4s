package org.http4s

import java.net.InetSocketAddress

import org.http4s.headers.`Content-Type`

import scalaz.concurrent.Task


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
  }

  "Message" should {
    "decode" >> {
      "produce a UnsupportedMediaType in the event of a decode failure" >> {
        "MediaTypeMismatch" in {
          val req = Request(headers = Headers(`Content-Type`(MediaType.`application/base64`)))
          req.decodeWith(EntityDecoder.text, strict = true)(txt => Task.now(Response())).run.status must_==
            Status.UnsupportedMediaType
        }
        "MediaTypeMissing" in {
          val req = Request()
          req.decodeWith(EntityDecoder.text, strict = true)(txt => Task.now(Response())).run.status must_==
            Status.UnsupportedMediaType
        }
      }
    }
  }

}
