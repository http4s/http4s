package org.http4s.server.middleware

import org.http4s._
import org.http4s.server._
import scodec.bits.ByteVector

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.stream.process1

object EntityLimiter {

  case class EntityTooLarge(limit: Int) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Int = Http4sConfig.getInt("org.http4s.default-max-entity-size")

  def apply(route: HttpService, limit: Int = DefaultMaxEntitySize): HttpService = new HttpService {
    override def isDefinedAt(x: Request): Boolean = route.isDefinedAt(x)

    override def apply(v1: Request): Task[Response] =
      route.apply(v1.copy(body = v1.body |> takeBytes(limit)))

    override def applyOrElse[A1 <: Request, B1 >: Task[Response]](x: A1, default: (A1) => B1): B1 =
      route.applyOrElse(x.copy(body = x.body |> takeBytes(limit)), {_: Request => default(x)})
  }

  private def takeBytes(n: Int): Process1[ByteVector, ByteVector] = {
    def go(taken: Int, chunk: ByteVector): Process1[ByteVector, ByteVector] = {
      val sz = taken + chunk.length
      if (sz > n) fail(EntityTooLarge(n))
      else Emit(Seq(chunk), await(Get[ByteVector])(go(sz, _)))
    }
    await(Get[ByteVector])(go(0,_))
  }

  def comsumeUpTo(n: Int): Process1[ByteVector, ByteVector] = {
    val p = process1.fold[ByteVector, ByteVector](ByteVector.empty) { (c1, c2) => c1 ++ c2 }
    takeBytes(n) |> p
  }

}
