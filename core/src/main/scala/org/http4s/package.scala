package org

import http4s.ext.Http4sString
import scala.concurrent.{ExecutionContext, Promise, Future}
import com.typesafe.config.{ConfigFactory, Config}
import scalaz.{-\/, \/-, Semigroup, ~>}
import scalaz.concurrent.Task
import scalaz.syntax.id._
import scalaz.stream.Process
import scala.util.{Failure, Success}
import org.joda.time.{DateTime, DateTimeZone, ReadableInstant}
import org.joda.time.format.DateTimeFormat
import java.util.Locale

package object http4s {

  /** A function which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = Request => Task[Response]

  /** The scalaz.stream.Process[Task,Chunk] representing the body of the [[Response]]
     */
  type HttpBody = Process[Task, Chunk]
  object HttpBody {
    val empty = Process.halt
  }

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

}
