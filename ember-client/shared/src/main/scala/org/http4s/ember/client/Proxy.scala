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

import cats.effect.kernel.Sync
import cats.effect.std.Env
import cats.effect.std.SystemProperties
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.BasicCredentials
import org.http4s.ParseFailure
import org.http4s.ParseResult
import org.http4s.Uri

/** A proxy through which Ember tunnels connections to origin servers. */
sealed trait Proxy extends Product with Serializable

final case class HttpProxy(
    scheme: Uri.Scheme,
    host: Host,
    port: Option[Port] = None,
    credentials: Option[BasicCredentials] = None,
) extends Proxy

final case class Socks4Proxy(host: Host, port: Port, username: Option[String] = None) extends Proxy

final case class Socks5Credentials(username: String, password: String)

final case class Socks5Proxy(host: Host, port: Port, credentials: Option[Socks5Credentials] = None)
    extends Proxy

object Proxy {

  // https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html
  private val PropertyKeys = List(
    "http.proxyHost",
    "http.proxyPort",
    "https.proxyHost",
    "https.proxyPort",
    "socksProxyHost",
    "socksProxyPort",
    "socksProxyVersion",
    "java.net.socks.username",
    "java.net.socks.password",
  )

  /** Derives a proxy from the JVM networking system properties, falling back to the conventional
    * `*_proxy` environment variables (first of `http_proxy`, `https_proxy` and `all_proxy` is
    * taken).
    */
  def fromEnvironment[F[_]: Sync]: F[Option[Proxy]] = {
    val properties = SystemProperties.make[F]
    for {
      found <- PropertyKeys.traverse(key => properties.get(key).map(key -> _))
      variables <- Env.make[F].entries
    } yield fromEnvironment(
      found.collect { case (key, Some(value)) => key -> value }.toMap,
      variables.toMap,
    )
  }

  private[client] def fromEnvironment(
      properties: Map[String, String],
      variables: Map[String, String],
  ): Option[Proxy] =
    fromProperties(properties).orElse(fromVariables(variables))

  private def fromProperties(properties: Map[String, String]): Option[Proxy] = {
    // Neither the `http` nor the `https` property says how the proxy itself is reached, only which
    // origins it serves, and the JVM reaches it in the clear either way. Only the default port
    // differs.
    def http(prefix: String, defaultPort: Port): Option[Proxy] =
      properties
        .get(s"$prefix.proxyHost")
        .flatMap(Host.fromString)
        .map { host =>
          val port = properties.get(s"$prefix.proxyPort").flatMap(Port.fromString)
          HttpProxy(Uri.Scheme.http, host, port.orElse(defaultPort.some))
        }

    def socks: Option[Proxy] =
      properties.get("socksProxyHost").flatMap(Host.fromString).map { host =>
        val port = properties.get("socksProxyPort").flatMap(Port.fromString).getOrElse(port"1080")
        val username = properties.get("java.net.socks.username")
        if (properties.get("socksProxyVersion").contains("4"))
          Socks4Proxy(host, port, username)
        else
          Socks5Proxy(
            host,
            port,
            (username, properties.get("java.net.socks.password")).mapN(Socks5Credentials.apply),
          )
      }

    http("http", port"80").orElse(http("https", port"443")).orElse(socks)
  }

  private def fromVariables(variables: Map[String, String]): Option[Proxy] = {
    // Under CGI, HTTP_PROXY can be set by untrusted client's headers (CVE-2016-5385),
    // so it's ignored when REQUEST_METHOD CGI marker is present.
    val http =
      if (variables.contains("REQUEST_METHOD")) none[String]
      else variable(variables, "http_proxy")

    http
      .orElse(variable(variables, "https_proxy"))
      .orElse(variable(variables, "all_proxy"))
      .flatMap(fromString(_).toOption)
  }

  private def variable(variables: Map[String, String], name: String): Option[String] =
    List(name, name.toUpperCase).collectFirstSome(variables.get).filter(_.nonEmpty)

  /** Parses `[scheme://][user[:password]@]host[:port]`.
    *
    * The scheme, which defaults to `http`, says how the proxy is reached:
    * `http` or `https` for an [[HttpProxy]], `socks4` or `socks4a` for a [[Socks4Proxy]], and
    * `socks5` or `socks5h` for a [[Socks5Proxy]].
    */
  def fromString(value: String): ParseResult[Proxy] =
    parse(value).toRight(ParseFailure("Invalid proxy", s"Invalid proxy: $value"))

  private def parse(value: String): Option[Proxy] = {
    val absolute = if (value.contains("://")) value else s"http://$value"

    for {
      uri <- Uri.fromString(absolute).toOption
      authority <- uri.authority
      host <- Host.fromString(authority.host.value)
      scheme <- uri.scheme
      port = authority.port.flatMap(Port.fromInt)
      username = authority.userInfo.map(_.username)
      password = authority.userInfo.flatMap(_.password)
      basic = username.map(BasicCredentials(_, password.getOrElse("")))
      proxy <- scheme.value.toLowerCase match {
        case "http" => HttpProxy(Uri.Scheme.http, host, port, basic).some
        case "https" => HttpProxy(Uri.Scheme.https, host, port, basic).some
        case "socks4" | "socks4a" => Socks4Proxy(host, port.getOrElse(port"1080"), username).some
        case "socks5" | "socks5h" =>
          Socks5Proxy(
            host,
            port.getOrElse(port"1080"),
            (username, password).mapN(Socks5Credentials.apply),
          ).some
        case _ => none[Proxy]
      }
    } yield proxy
  }
}
