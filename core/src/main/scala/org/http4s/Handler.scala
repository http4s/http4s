package org.http4s

import scala.concurrent.Future

import play.api.libs.iteratee._

object Handler {
  def apply(result: Response): Handler = Done(result)

  def apply(result: Future[Response]): Handler = Iteratee.fold1[Array[Byte], Response](result) { (_, _) => result }
}
