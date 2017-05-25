package org.http4s
package client

import java.io.IOException

import org.http4s.Http4sSpec
import org.http4s.headers.Accept
import org.http4s.Status.InternalServerError

import scalaz.-\/
import scalaz.concurrent.Task
import scalaz.stream.Process

import org.http4s.Status.{Ok, NotFound, Created, BadRequest}
import org.http4s.Method._
import org.http4s.Uri.uri

class ProxySelectorSpec extends Http4sSpec {
  val httpExampleDotCom =
    RequestKey("http".ci, Uri.Authority(
      host = Uri.RegName("example.com"),
      port = Some(80)
    ))
  val httpsExampleDotOrg =
    RequestKey("https".ci, Uri.Authority(
      host = Uri.RegName("example.org"),
      port = Some(443)
    ))

  "properties proxy selector" should {
    "proxy http requests" in {
      propertiesProxyConfig(Map(
        "http.proxyHost" -> "localhost",
        "http.proxyPort" -> "8888"
      )).lift(httpExampleDotCom) must beSome(ProxyConfig(
        "http".ci, Uri.RegName("localhost"), 8888, None))
    }

    "default http to port 80 when no http.proxyPort" in {
      propertiesProxyConfig(Map(
        "http.proxyHost" -> "localhost"
      )).lift(httpExampleDotCom) must beSome(ProxyConfig(
        "http".ci, Uri.RegName("localhost"), 80, None))
    }

    "not proxy http without a proxy host" in {
      propertiesProxyConfig(Map(
        "https.proxyHost" -> "localhost",
      )).lift(httpExampleDotCom) must beNone
    }

    "honor literal matches in nonProxyHosts for http" in {
      propertiesProxyConfig(Map(
        "https.proxyHost" -> "localhost",
        "http.nonProxyHosts" -> "localhost|example.com"
      )).lift(httpExampleDotCom) must beNone
    }

    "honor wildcard matches in nonProxyHosts for http" in {
      propertiesProxyConfig(Map(
        "http.proxyHost" -> "localhost",
        "http.nonProxyHosts" -> "localhost|example.*"
      )).lift(httpExampleDotCom) must beNone
    }

    "proxy https requests" in {
      propertiesProxyConfig(Map(
        "https.proxyHost" -> "localhost",
        "https.proxyPort" -> "8443"
      )).lift(httpsExampleDotOrg) must beSome(ProxyConfig(
        "https".ci, Uri.RegName("localhost"), 8443, None))
    }

    "default https to port 443 when no https.proxyPort" in {
      propertiesProxyConfig(Map(
        "https.proxyHost" -> "localhost"
      )).lift(httpsExampleDotOrg) must beSome(ProxyConfig(
        "https".ci, Uri.RegName("localhost"), 443, None))
    }

    "not proxy http without a proxy host" in {
      propertiesProxyConfig(Map(
        "http.proxyHost" -> "localhost",
      )).lift(httpsExampleDotOrg) must beNone
    }

    "honor literal matches in nonProxyHosts for https" in {
      propertiesProxyConfig(Map(
        "https.proxyHost" -> "localhost",
        "http.nonProxyHosts" -> "localhost|example.org"
      )).lift(httpsExampleDotOrg) must beNone
    }

    "honor wildcard matches in nonProxyHosts for https" in {
      propertiesProxyConfig(Map(
        "https.proxyHost" -> "localhost",
        "http.nonProxyHosts" -> "localhost|example.*"
      )).lift(httpsExampleDotOrg) must beNone
    }
  }
}
