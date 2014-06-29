package org

import com.typesafe.config.{ConfigFactory, Config}
import scalaz.concurrent.Task
import scalaz.stream.Process
import org.http4s.util.CaseInsensitiveString
import scodec.bits.ByteVector

package object http4s {

  /** A PartialFunction which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = PartialFunction[Request,Task[Response]]

  type AuthScheme = CaseInsensitiveString

  /** The scalaz.stream.Process[Task,Chunk] representing the body of the [[Response]]
     */
  type HttpBody = Process[Task, ByteVector]

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

  val ApiVersion: Http4sVersion = Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)
}
