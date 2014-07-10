package org.http4s

import scalaz.concurrent.Task

package object server {
  /** A PartialFunction which defines the transformation of [[Request]] to a scalaz.concurrent.Task[Response]
    * containing the [[Response]]
    */
  type HttpService = PartialFunction[Request, Task[Response]]
}
