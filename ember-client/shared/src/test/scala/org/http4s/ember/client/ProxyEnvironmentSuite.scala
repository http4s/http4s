/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.client

import cats.Id
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.BasicCredentials
import org.http4s.Http4sSuite
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.RequestKey

class ProxyEnvironmentSuite extends Http4sSuite {

  private def proxy(
      properties: Map[String, String] = Map.empty,
      variables: Map[String, String] = Map.empty,
  ): Option[Proxy] =
    Proxy.fromEnvironment(properties, variables)

  private def key(uri: String): RequestKey =
    RequestKey.fromRequest(Request[Id](uri = Uri.unsafeFromString(uri)))

  test("an http_proxy variable names an HttpProxy") {
    assertEquals(
      proxy(variables = Map("http_proxy" -> "http://proxy.example:3128")),
      Some(HttpProxy(Uri.Scheme.http, host"proxy.example", port"3128".some)),
    )
  }

  test("a variable may omit the scheme, the port and the credentials") {
    assertEquals(
      proxy(variables = Map("http_proxy" -> "proxy.example")),
      Some(HttpProxy(Uri.Scheme.http, host"proxy.example", None)),
    )
  }

  test("a variable may carry credentials") {
    assertEquals(
      proxy(variables = Map("http_proxy" -> "http://user:pass@proxy.example:3128")),
      Some(
        HttpProxy(
          Uri.Scheme.http,
          host"proxy.example",
          port"3128".some,
          BasicCredentials("user", "pass").some,
        )
      ),
    )
  }

  test("an https scheme means the proxy itself is reached over TLS") {
    assertEquals(
      proxy(variables = Map("https_proxy" -> "https://proxy.example")),
      Some(HttpProxy(Uri.Scheme.https, host"proxy.example", None)),
    )
  }

  test("the socks schemes name a SOCKS proxy, defaulting to port 1080") {
    assertEquals(
      proxy(variables = Map("all_proxy" -> "socks5://proxy.example")),
      Some(Socks5Proxy(host"proxy.example", port"1080")),
    )
    assertEquals(
      proxy(variables = Map("all_proxy" -> "socks5h://user:pass@proxy.example:1081")),
      Some(Socks5Proxy(host"proxy.example", port"1081", Socks5Credentials("user", "pass").some)),
    )
    assertEquals(
      proxy(variables = Map("all_proxy" -> "socks4a://ident@proxy.example")),
      Some(Socks4Proxy(host"proxy.example", port"1080", "ident".some)),
    )
  }

  test("an unknown scheme names no proxy") {
    assertEquals(proxy(variables = Map("all_proxy" -> "ftp://proxy.example")), None)
  }

  test("a variable is read in either case, the lower case winning") {
    assertEquals(
      proxy(variables = Map("HTTP_PROXY" -> "http://upper.example")),
      Some(HttpProxy(Uri.Scheme.http, host"upper.example", None)),
    )
    assertEquals(
      proxy(variables =
        Map("http_proxy" -> "http://lower.example", "HTTP_PROXY" -> "http://upper.example")
      ),
      Some(HttpProxy(Uri.Scheme.http, host"lower.example", None)),
    )
  }

  test("an empty variable reads as unset") {
    assertEquals(
      proxy(variables = Map("http_proxy" -> "", "https_proxy" -> "http://set.example")),
      Some(HttpProxy(Uri.Scheme.http, host"set.example", None)),
    )
  }

  test("http_proxy is refused under a CGI gateway, where a request header supplies it") {
    val attacker = Map("http_proxy" -> "http://attacker.example", "REQUEST_METHOD" -> "GET")
    assertEquals(proxy(variables = attacker), None)
    assertEquals(proxy(variables = attacker + ("HTTP_PROXY" -> "http://attacker.example")), None)

    assertEquals(
      proxy(variables = attacker + ("https_proxy" -> "http://safe.example")),
      Some(HttpProxy(Uri.Scheme.http, host"safe.example", None)),
    )

    assertEquals(
      proxy(variables = attacker - "REQUEST_METHOD"),
      Some(HttpProxy(Uri.Scheme.http, host"attacker.example", None)),
    )
  }

  test("fromString parses a proxy and explains a bad one") {
    assertEquals(
      Proxy.fromString("socks5://user:pass@proxy.example:1081"),
      Right(Socks5Proxy(host"proxy.example", port"1081", Socks5Credentials("user", "pass").some)),
    )
    assert(Proxy.fromString("ftp://proxy.example").isLeft)
    assert(Proxy.fromString("").isLeft)
  }

  test("the variables collapse to the first of http, https and all") {
    val all = Map(
      "http_proxy" -> "http://first.example",
      "https_proxy" -> "http://second.example",
      "all_proxy" -> "http://third.example",
    )
    assertEquals(
      proxy(variables = all),
      Some(HttpProxy(Uri.Scheme.http, host"first.example", None)),
    )
    assertEquals(
      proxy(variables = all - "http_proxy"),
      Some(HttpProxy(Uri.Scheme.http, host"second.example", None)),
    )
    assertEquals(
      proxy(variables = all - "http_proxy" - "https_proxy"),
      Some(HttpProxy(Uri.Scheme.http, host"third.example", None)),
    )
  }

  test("the properties name a proxy reached in the clear, defaulting the port per scheme") {
    assertEquals(
      proxy(properties = Map("http.proxyHost" -> "proxy.example", "http.proxyPort" -> "3128")),
      Some(HttpProxy(Uri.Scheme.http, host"proxy.example", port"3128".some)),
    )
    assertEquals(
      proxy(properties = Map("http.proxyHost" -> "proxy.example")),
      Some(HttpProxy(Uri.Scheme.http, host"proxy.example", port"80".some)),
    )
    // https.proxyHost serves https targets but is still reached in the clear.
    assertEquals(
      proxy(properties = Map("https.proxyHost" -> "proxy.example")),
      Some(HttpProxy(Uri.Scheme.http, host"proxy.example", port"443".some)),
    )
  }

  test("the socks properties name a SOCKS proxy, version 5 unless told otherwise") {
    assertEquals(
      proxy(properties = Map("socksProxyHost" -> "proxy.example")),
      Some(Socks5Proxy(host"proxy.example", port"1080")),
    )
    assertEquals(
      proxy(properties =
        Map(
          "socksProxyHost" -> "proxy.example",
          "socksProxyVersion" -> "4",
          "java.net.socks.username" -> "ident",
        )
      ),
      Some(Socks4Proxy(host"proxy.example", port"1080", "ident".some)),
    )
    assertEquals(
      proxy(properties =
        Map(
          "socksProxyHost" -> "proxy.example",
          "java.net.socks.username" -> "user",
          "java.net.socks.password" -> "pass",
        )
      ),
      Some(Socks5Proxy(host"proxy.example", port"1080", Socks5Credentials("user", "pass").some)),
    )
  }

  test("the properties win over the variables") {
    assertEquals(
      proxy(
        properties = Map("http.proxyHost" -> "property.example"),
        variables = Map("http_proxy" -> "http://variable.example"),
      ),
      Some(HttpProxy(Uri.Scheme.http, host"property.example", port"80".some)),
    )
  }

  test("an empty environment names no proxy") {
    assertEquals(proxy(), None)
  }

  test("the ignored hosts come from nonProxyHosts, then no_proxy, then the default") {
    val fromProperty =
      IgnoredHosts.fromEnvironment("*.corp".some, Map("no_proxy" -> ".example.com"))
    assert(fromProperty.ignores(key("http://host.corp/")))
    assert(!fromProperty.ignores(key("http://host.example.com/")))

    val fromVariable = IgnoredHosts.fromEnvironment(None, Map("NO_PROXY" -> ".example.com"))
    assert(fromVariable.ignores(key("http://host.example.com/")))
    assert(!fromVariable.ignores(key("http://localhost/")))

    // Neither set, so the JVM's own default applies.
    assert(IgnoredHosts.fromEnvironment(None, Map.empty).ignores(key("http://localhost/")))
  }

  test("an empty no_proxy ignores nothing") {
    assertEquals(
      IgnoredHosts.fromEnvironment(None, Map("no_proxy" -> "")).toString,
      IgnoredHosts.none.toString,
    )
    assertEquals(
      IgnoredHosts.fromEnvironment("".some, Map.empty).toString,
      IgnoredHosts.none.toString,
    )
  }
}
