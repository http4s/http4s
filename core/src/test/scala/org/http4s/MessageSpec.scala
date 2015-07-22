package org.http4s

import java.net.InetSocketAddress


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

}
