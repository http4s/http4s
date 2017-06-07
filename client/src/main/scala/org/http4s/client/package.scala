package org.http4s

import cats._
import cats.effect.IO
import org.http4s.Method.{NoBody, PermitsBody}
import org.http4s.client.impl.{EmptyRequestGenerator, EntityRequestGenerator}

/** Provides extension methods for using a http4s [[org.http4s.client.Client]]
  * {{{
  *   import cats.effect.IO
  *   import org.http4s._
  *   import org.http4s.client._
  *   import org.http4s.Http4s._
  *   import org.http4s.Status._
  *   import org.http4s.Method._
  *   import org.http4s.EntityDecoder
  *
  *   def client: Client[IO] = ???
  *
  *   val r: IO[String] = client(GET(uri("https://www.foo.bar/"))).as[String]
  *   val r2: DecodeResult[String] = client(GET(uri("https://www.foo.bar/"))).attemptAs[String] // implicitly resolve the decoder
  *   val req1 = r.unsafeRunSync
  *   val req2 = r.unsafeRunSync // Each invocation fetches a new Result based on the behavior of the Client
  *
  * }}}
  */
package object client extends Http4sClientDsl[IO] with ClientTypes

trait ClientTypes {
  import org.http4s.client._

  type ConnectionBuilder[F[_], A <: Connection[F]] = RequestKey => F[A]

  type Middleware[F[_]] = Client[F] => Client[F]
}

trait Http4sClientDsl[F[_]] {
  import Http4sClientDsl._

  implicit def http4sWithBodySyntax(method: Method with PermitsBody): WithBodyOps[F] =
    new WithBodyOps[F](method)

  implicit def http4sNoBodyOps(method: Method with NoBody): NoBodyOps[F] =
    new NoBodyOps[F](method)

  implicit def http4sHeadersDecoder[T](implicit F: Applicative[F],
                                       decoder: EntityDecoder[F, T]): EntityDecoder[F, (Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail: _*)(resp => decoder.decode(resp, strict = true).map(t => (resp.headers, t)))
  }
}

object Http4sClientDsl {

  /** Syntax classes to generate a request directly from a [[Method]] */
  implicit class WithBodyOps[F[_]](val method: Method with PermitsBody) extends AnyVal with EntityRequestGenerator[F]
  implicit class NoBodyOps[F[_]](val method: Method with NoBody)        extends AnyVal with EmptyRequestGenerator[F]
}
