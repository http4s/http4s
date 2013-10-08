package org

import http4s.attributes._
import http4s.ext.Http4sString
import scala.concurrent.{ExecutionContext, Promise, Future}
import com.typesafe.config.{ConfigFactory, Config}
import org.http4s.attributes.RequestScope
import org.http4s.attributes.AppScope
import scalaz.{-\/, \/-, Semigroup, ~>}
import scalaz.concurrent.Task
import scalaz.syntax.either._
import scalaz.stream.Process
import scala.util.{Failure, Success}


package object http4s {
  type HttpService = Request => Task[Response]
  type HttpBody = Process[Task, HttpChunk]

  implicit val HttpChunkSemigroup: Semigroup[HttpChunk] = Semigroup.instance {
    case (a: BodyChunk, b: BodyChunk) => a ++ b
    case (a: BodyChunk, _) => a
    case (_, b: BodyChunk) => b
    case (_, _) => BodyChunk.empty
  }
  
  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  // TODO: why should this have a scope? it just generates routes...
  trait RouteHandler { self =>
    def apply(): HttpService
  }

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

//  implicit def request2scope(req: RequestPrelude) = RequestScope(req.uuid)
//  implicit def app2scope(routes: RouteHandler) = routes.appScope
//  implicit def attribute2defaultScope[T, S <: Scope](attributeKey: AttributeKey[T])(implicit scope: S) = attributeKey in scope
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

  implicit val taskToFuture: Task ~> Future = new (Task ~> Future) {
    def apply[A](task: Task[A]): Future[A] = {
      val p = Promise[A]()
      task.runAsync {
        case \/-(a) => p.success(a)
        case -\/(t) => p.failure(t)
      }
      p.future
    }
  }

  implicit def futureToTask(implicit ec: ExecutionContext): Future ~> Task = new (Future ~> Task) {
    def apply[A](future: Future[A]): Task[A] = {
      Task.async { f =>
        future.onComplete {
          case Success(a) => f(a.right)
          case Failure(t) => f(t.left)
        }
      }
    }
  }
}

