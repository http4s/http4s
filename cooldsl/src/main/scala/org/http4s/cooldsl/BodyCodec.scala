package org.http4s
package cooldsl

import scalaz.concurrent.Task
import shapeless.HNil
import scalaz.{-\/, \/-, \/}

/**
 * Created by Bryce Anderson on 4/27/14.
 */
object BodyCodec {

  private type Result[T] = Task[String\/T]

  trait BodyTransformer[T] {
    def decode(req: Request): Result[T]
    def validations: HeaderRule[HNil]
  }

  case class Decoder[T](decoder: Request => Result[T], validations: HeaderRule[HNil]) extends BodyTransformer[T] {
    override def decode(req: Request): Result[T] = decoder(req)
  }

  case class OrDec[T](c1: Decoder[T], c2: Decoder[T]) extends BodyTransformer[T] {
    override val validations: HeaderRule[HNil] = Or(c1.validations, c2.validations)

    override def decode(req: Request): Result[T] = {
      RouteExecutor.ensureValidHeaders(c1.validations,req) match {
        case \/-(_)    => c1.decode(req)
        case e@ -\/(s) =>
          RouteExecutor.ensureValidHeaders(c1.validations,req) match {
            case \/-(_) => c2.decode(req)
            case -\/(_) => Task.now(e)    // Use the first error message arbitrarily
          }
      }
    }
  }

  implicit val strDec = {
    val dec: Request => Result[String] = _.body.runLog.map(vs => \/-(new String(vs.reduce(_ ++ _).toArray)))
    Decoder(dec, EmptyHeaderRule)
  }

  implicit def reqDecoder[T](f: Request => Task[T]) =
    Decoder(f.andThen(_.attempt.map(_.leftMap(t => t.getMessage))), EmptyHeaderRule)

  implicit def bodyDecoder[T](f: HttpBody => Task[T]) = reqDecoder(r => f(r.body))

}
