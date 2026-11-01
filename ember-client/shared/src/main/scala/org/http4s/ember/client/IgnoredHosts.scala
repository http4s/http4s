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
import com.comcast.ip4s.Host
import com.comcast.ip4s.Ipv6Address
import org.http4s.client.RequestKey

import java.util.regex.Pattern.quote
import scala.util.matching.Regex

/** Hosts reached directly rather than through a [[Proxy]], in the syntax of the JVM's
  * `http.nonProxyHosts` property.
  */
final class IgnoredHosts private (private val pattern: Option[Regex]) {

  /** Whether requests for `requestKey` bypass the proxy. */
  def ignores(requestKey: RequestKey): Boolean =
    matches(requestKey.authority.host.renderString)

  /** Whether connections to `host` bypass the proxy. */
  def ignores(host: Host): Boolean =
    matches(host match {
      // Mirrors how Uri.Host renders an address, so one mask covers both.
      case host: Ipv6Address => s"[$host]"
      case host => host.toString
    })

  private def matches(host: String): Boolean =
    pattern.exists(_.pattern.matcher(host.toLowerCase).matches())

  override def toString: String =
    s"IgnoredHosts(${pattern.fold("")(_.pattern.pattern())})"
}

object IgnoredHosts {

  /** Ignores no host, so every request goes through the `Proxy`. */
  val none: IgnoredHosts = new IgnoredHosts(None)

  /** The hosts the JVM ignores by default. */
  val default: IgnoredHosts = fromString("localhost|127.*|[::1]").getOrElse(none)

  /** Derives the ignored hosts from `http.nonProxyHosts`, falling back to `no_proxy`, and to
    * [[default]] when the environment names neither.
    */
  def fromEnvironment[F[_]: Sync]: F[IgnoredHosts] =
    for {
      property <- SystemProperties.make[F].get("http.nonProxyHosts")
      variables <- Env.make[F].entries
    } yield fromEnvironment(property, variables.toMap)

  private[client] def fromEnvironment(
      nonProxyHosts: Option[String],
      variables: Map[String, String],
  ): IgnoredHosts =
    nonProxyHosts
      .map(mask => fromString(mask).getOrElse(none))
      .orElse(
        variables
          .get("no_proxy")
          .orElse(variables.get("NO_PROXY"))
          .map(list => fromNoProxy(list).getOrElse(none))
      )
      .getOrElse(default)

  /** Parses a `no_proxy` list, the syntax the `*_proxy` environment variables are written in:
    * hosts separated by commas, where a leading dot marks a suffix and so covers the domain
    * itself along with its subdomains. Any other entry is taken as [[fromString]] takes it, so
    * `*` still wildcards. Yields `None` when the list names no host.
    */
  def fromNoProxy(list: String): Option[IgnoredHosts] =
    fromString(toMask(list))

  private def toMask(noProxy: String): String =
    noProxy
      .split(',')
      .iterator
      .map(_.trim)
      .filter(_.nonEmpty)
      // A leading dot reads as "and its subdomains", which the mask spells as two disjuncts.
      .flatMap(host => if (host.startsWith(".")) List(host.drop(1), s"*$host") else List(host))
      .mkString("|")

  /** Parses a `|` separated mask, yielding `None` when it names no host. */
  def fromString(mask: String): Option[IgnoredHosts] = {
    val joined = mask
      .split('|')
      .iterator
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .map(toRegex)
      .mkString("|")

    if (joined.isEmpty) Option.empty[IgnoredHosts]
    else new IgnoredHosts(joined.r.some).some
  }

  private def toRegex(disjunct: String): String =
    disjunct match {
      case "*" => ".*"
      case host if host.startsWith("*") && host.endsWith("*") =>
        ".*" + quote(host.substring(1, host.length - 1)) + ".*"
      case host if host.startsWith("*") => ".*" + quote(host.substring(1))
      case host if host.endsWith("*") => quote(host.dropRight(1)) + ".*"
      case host => quote(host)
    }
}
