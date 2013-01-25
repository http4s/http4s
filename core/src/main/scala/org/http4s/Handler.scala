package org.http4s

import scala.concurrent.Future

import play.api.libs.iteratee._

object Handler {
  def apply(result: Result): Handler = Done(result)

  def apply(result: Future[Result]): Handler = Iteratee.fold1[Array[Byte], Result](result) { (_, _) => result }
}
