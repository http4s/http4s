package org.http4s

import java.nio.charset.{ Charset => NioCharset, StandardCharsets }
import org.http4s.Method.{ PermitsBody, NoBody}
import org.http4s.client.impl.{EmptyRequestGenerator, EntityRequestGenerator}
import org.http4s.parser.RequestUriParser
import org.http4s.syntax.string._
import org.http4s.util.CaseInsensitiveString

import scala.util.Try
import scala.util.matching.Regex

import scalaz.concurrent.Task

/** Provides extension methods for using the a http4s [[org.http4s.client.Client]]
  * {{{
  *   import scalaz.concurrent.Task
  *   import org.http4s._
  *   import org.http4s.client._
  *   import org.http4s.Http4s._
  *   import org.http4s.Status._
  *   import org.http4s.Method._
  *   import org.http4s.EntityDecoder
  *
  *   def client: Client = ???
  *
  *   val r: Task[String] = client(GET(uri("https://www.foo.bar/"))).as[String]
  *   val r2: DecodeResult[String] = client(GET(uri("https://www.foo.bar/"))).attemptAs[String] // implicitly resolve the decoder
  *   val req1 = r.run
  *   val req2 = r.run // Each invocation fetches a new Result based on the behavior of the Client
  *
  * }}}
  */

package object client {

  type ConnectionBuilder[A <: Connection] = RequestKey => Task[A]

  type Middleware = Client => Client

  /** Syntax classes to generate a request directly from a [[Method]] */
  implicit class WithBodySyntax(val method: Method with PermitsBody) extends AnyVal with EntityRequestGenerator
  implicit class NoBodySyntax(val method: Method with NoBody) extends AnyVal with EmptyRequestGenerator


  implicit def wHeadersDec[T](implicit decoder: EntityDecoder[T]): EntityDecoder[(Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail:_*)(resp => decoder.decode(resp, strict = true).map(t => (resp.headers,t)))
  }

  /** Chooses a proxy for the request from its request key */
  type ProxySelector = PartialFunction[RequestKey, ProxyConfig]

  /** Implementation of a proxy selector based on the standard Java
    * system properties. */
  def systemPropertiesProxyConfig: ProxySelector = {
    val nonProxyHosts = sys.props.get("http.nonProxyHosts") match {
      case Some(nph) =>
        nph.split('|').toList.map { host =>
          new Regex(Regex.quote(host.replaceAllLiterally("*", ".*"))).pattern
        }
      case None => Nil
    }
    println(nonProxyHosts)

    def skipProxy(authority: Uri.Authority) =
      nonProxyHosts.exists(_.matcher(authority.host.toString).matches)

    def configForScheme(scheme: CaseInsensitiveString) =
      for {
        rawHost <- sys.props.get(s"${scheme}.proxyHost")
        host <- new RequestUriParser(rawHost, StandardCharsets.UTF_8).Host.run().toOption
        rawPort <- sys.props.get(s"${scheme}.proxyPort").orElse(Some("80"))
        port <- Try(rawPort.toInt).toOption
      } yield ProxyConfig(scheme, host, port, None)

    def selector(scheme: CaseInsensitiveString) =
      configForScheme(scheme).fold(PartialFunction.empty: ProxySelector) { cfg => {
        case RequestKey(rScheme, authority)
            if rScheme == scheme && !skipProxy(authority) =>
          cfg
      }}

    selector("https".ci) orElse selector("http".ci)
  }
}
