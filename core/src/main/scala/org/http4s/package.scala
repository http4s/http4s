package org

import scalaz.{Kleisli, EitherT, \/}

import scalaz.concurrent.Task
import scalaz.stream.Process
import org.http4s.util.CaseInsensitiveString
import scodec.bits.ByteVector

package object http4s { // scalastyle:ignore

  type AuthScheme = CaseInsensitiveString

  type EntityBody = Process[Task, ByteVector]

  def EmptyBody: EntityBody = Process.halt

  type DecodeResult[T] = EitherT[Task, DecodeFailure, T]

  val ApiVersion: Http4sVersion = Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)

  type ParseResult[+A] = ParseFailure \/ A

  val DefaultCharset = Charset.`UTF-8`

  /**
    * A [[Service]] that produces a Task to compute a [[Response]] from a
    * [[Request]].  An HttpService can be run on any supported http4s
    * server backend, such as Blaze, Jetty, or Tomcat.
    */
  type HttpService = Service[Request, Response]

  /* Lives here to work around https://issues.scala-lang.org/browse/SI-7139 */
  /**
    * There are 4 HttpService constructors:
    * <ul>
    *  <li>(Request => Task[Response]) => HttpService</li>
    *  <li>PartialFunction[Request, Task[Response]] => HttpService</li>
    *  <li>(PartialFunction[Request, Task[Response]], HttpService) => HttpService</li>
    *  <li>(PartialFunction[Request, Task[Response]], Task[Response]) => HttpService</li>
    * </ul>
    */
  object HttpService {
    def apply(pf: PartialFunction[Request, Task[Response]]): HttpService =
      Service(pf)

    /**
      * Lifts a (total) function to an `HttpService`. The function is expected to handle
      * ALL requests it is given.
      */
    def lift(f: Request => Task[Response]): HttpService = Service.lift(f)

    /** The default 'Not Found' response used when lifting a partial function
      * to a [[HttpService]] or general 'not handled' results.
      */
    val notFound: Task[Response] =
      Task.now {
        // Task.now(task.run) looks weird, but this memoizes it so we don't
        // constantly recreate it.
        Response(Status.NotFound)
          .withBody("404 Not Found.").run
      }

    val empty   : HttpService    = Service.const(notFound)
  }

  type Callback[A] = Throwable \/ A => Unit

  /** A stream of server-sent events */
  type EventStream = Process[Task, ServerSentEvent]
}
