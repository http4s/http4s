// TODO fs2 port

package org.http4s

import org.http4s.client.impl.{EmptyRequestGenerator, EntityRequestGenerator}
import Method.{ PermitsBody, NoBody}

import fs2._
// import cats.Functor
import cats.Monad

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

  // implicit val taskFunctor = new Functor[Task] {
  //   def map[A, B](fa: Task[A])(f: A => B): Task[B] = fa.map(f)
  // }

  implicit val taskMonad = new Monad[Task] {
    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] =
      fa.flatMap(f)

    override def map[A, B](fa: Task[A])(f: A => B): Task[B] = fa.map(f)

    def pure[A](a: A): Task[A] = Task.now(a)

  }


  implicit def wHeadersDec[T](implicit decoder: EntityDecoder[T]): EntityDecoder[(Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail:_*)(resp => decoder.decode(resp, strict = true).map(t => (resp.headers,t)))
  }

}
