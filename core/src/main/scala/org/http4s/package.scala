package org

import http4s.attributes._
import http4s.ext.Http4sString
import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.config.{ConfigFactory, Config}
import scalaz.stream.Process
import scalaz._
import org.http4s.attributes.RequestScope
import org.http4s.attributes.AppScope


package object http4s {
  type HttpService[F[_]] = Request[F] => Process[F, Response[F]]

  type HttpBody[+F[_]] = Process[F, HttpChunk]

  implicit val HttpChunkSemigroup: Semigroup[HttpChunk] = Semigroup.instance {
    case (a: BodyChunk, b: BodyChunk) => a ++ b
    case (a: BodyChunk, _) => a
    case (_, b: BodyChunk) => b
    case (_, _) => BodyChunk.empty
  }
  
  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  trait RouteHandler[F[_]] {
    implicit val appScope = AppScope()
    val attributes = appScope.newAttributesView()
    def apply(): HttpService[F]
  }

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

  implicit object GlobalState extends attributes.ServerContext {
    import attributes.ScopableAttributeKey

    private def scopedKey[T](key: Key[T]) = new ScopableAttributeKey[T](key) in ThisServer

    def apply[T](key: Key[T]): T = super.apply(scopedKey(key))
    def update[T](key: Key[T], value: T): T = super.update(scopedKey(key), value)
  }

  implicit def attribute2scoped[T](attributeKey: AttributeKey[T]) = new attributes.ScopableAttributeKey(attributeKey)
  implicit def request2scope(req: RequestPrelude) = RequestScope(req.uuid)
  implicit def app2scope[F[_]](routes: RouteHandler[F]) = routes.appScope
  implicit def attribute2defaultScope[T, S <: Scope](attributeKey: AttributeKey[T])(implicit scope: S) = attributeKey in scope
  implicit def string2headerkey(name: String): HttpHeaderKey[HttpHeader] = HttpHeaders.Key(name)

  val Get = Method.Get
  val Post = Method.Post
  val Put = Method.Put
  val Delete = Method.Delete
  val Trace = Method.Trace
  val Options = Method.Options
  val Patch = Method.Patch
  val Head = Method.Head
  val Connect = Method.Connect

  /*
  type RequestRewriter = PartialFunction[Request, Request]

  def rewriteRequest(f: RequestRewriter): Middleware = {
    route: Route => f.orElse({ case req: Request => req }: RequestRewriter).andThen(route)
  }

  type ResponseTransformer = PartialFunction[Response, Response]

  def transformResponse(f: ResponseTransformer): Middleware = {
    route: Route => route andThen { handler => handler.map(f) }
  }
  */

  // https://gist.github.com/stew/3900735 -- replace with scalaz-contrib
  implicit def futureFunctor(implicit executor: ExecutionContext) : Functor[Future] = new Functor[Future] {
    override def map[A,B](fa: Future[A])(f: A=>B) = fa map f
  }

  // https://gist.github.com/stew/3900735 -- replace with scalaz-contrib
  implicit def futureMonad(implicit executor: ExecutionContext) : Monad[Future] with Zip[Future] = new Monad[Future] with Zip[Future] {
    override def bind[A,B](fa: Future[A])(f: A=>Future[B]) = fa flatMap f
    override def point[A](a: => A) = Future(a)
    override def zip[A, B](a: => Future[A], b: => Future[B]) =
      for {
        x <- a
        y <- b
      } yield (x, y)

  }
}

