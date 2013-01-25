package org.http4s

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.iteratee._

object Handler {
  def apply(result: Result): Handler = Done(result)

  def apply(result: Future[Result])(implicit executor: ExecutionContext): Handler =
    Iteratee.fold1[Array[Byte], Result](result) { (state, input) => Future { state } }
}
