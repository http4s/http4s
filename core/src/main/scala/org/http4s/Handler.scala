package org.http4s

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.iteratee._

object Handler {
  def apply(result: Response): Handler = Done(result)

  def apply(result: Future[Response])(implicit executor: ExecutionContext): Handler =
    Iteratee.fold1[Array[Byte], Response](result) { (state, input) => Future { state } }
}
