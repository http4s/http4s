package org

import com.typesafe.config.{ConfigFactory, Config}
import scalaz.concurrent.Task
import scalaz.stream.Process
import org.http4s.util.CaseInsensitiveString

package object http4s {

  /** A PartialFunction which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = PartialFunction[Request,Task[Response]]

  type AuthScheme = CaseInsensitiveString

  /** The scalaz.stream.Process[Task,Chunk] representing the body of the [[Response]]
     */
  type HttpBody = Process[Task, Chunk]

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()
}
