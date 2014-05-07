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
    def consumes: Seq[MediaType]
    def force: Boolean

    private[BodyCodec] def checkMediaType(req: Request): Boolean = {
      if (!consumes.isEmpty) {
        req.headers.get(Header.`Content-Type`).map {
          h =>
            consumes.find {
              m => m == h.mediaType
            }.isDefined
        }.getOrElse(false)
      }
      else false
    }
  }

  case class Decoder[T](decoder: Request => Result[T], consumes: Seq[MediaType], force: Boolean) extends BodyTransformer[T] {
    override def decode(req: Request): Result[T] = decoder(req)
  }

  case class OrDec[T](c1: Decoder[T], c2: Decoder[T]) extends BodyTransformer[T] {

    override val consumes: Seq[MediaType] = c1.consumes ++ c2.consumes

    override def force: Boolean = c1.force || c2.force

    override def decode(req: Request): Result[T] = {
      if (c1.checkMediaType(req)) c1.decode(req)
      else if (c2.checkMediaType(req)) c2.decode(req)
      else if (c1.force) c1.decode(req)
      else if (c2.force) c2.decode(req)
      else Task.now(-\/(s"No suitable codec found. Supported media types: ${consumes.mkString(", ")}"))
    }
  }

  implicit val strDec = {
    val dec: Request => Result[String] = _.body.runLog.map(vs => \/-(new String(vs.reduce(_ ++ _).toArray)))
    Decoder(dec, MediaType.`text/plain`::Nil, true)
  }

  implicit def reqDecoder[T](f: Request => Task[T], mediaType: Seq[MediaType] = Nil, force: Boolean = true) =
    Decoder(f.andThen(_.attempt.map(_.leftMap(t => t.getMessage))), mediaType, force)

  implicit def bodyDecoder[T](f: HttpBody => Task[T]) = reqDecoder(r => f(r.body))

}
