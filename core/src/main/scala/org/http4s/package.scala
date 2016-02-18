package org

import scalaz.{Kleisli, EitherT, \/}

import scalaz.concurrent.Task
import scalaz.stream.Process
import org.http4s.util.CaseInsensitiveString
import scodec.bits.ByteVector

package object http4s {

  type AuthScheme = CaseInsensitiveString

  type EntityBody = Process[Task, ByteVector]

  def EmptyBody = Process.halt

  type DecodeResult[T] = EitherT[Task, DecodeFailure, T]

  val ApiVersion: Http4sVersion = Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)

  type ParseResult[+A] = ParseFailure \/ A

  val DefaultCharset = Charset.`UTF-8`

  /**
   * A Service wraps a function of request type [[A]] to a Task that runs
   * to esponse type [[B]].  By wrapping the `Service`, we can compose them
   * using Kleisli operations.
   */
  type Service[A, B] = Kleisli[Task, A, B]

  /**
    * A [[Service]] that produces a Task to compute a [[Response]] from a
    * [[Request]].  An HttpService can be run on any supported http4s
    * server backend, such as Blaze, Jetty, or Tomcat.
    */
  type HttpService = Service[Request, Response]

  type Callback[A] = Throwable \/ A => Unit
}
