package org.http4s.server.middleware

import org.http4s._
import org.http4s.server._
import scodec.bits.ByteVector

import scala.util.control.NoStackTrace
import scalaz.stream.{Process1, process1}
import scalaz.stream.Process._

object EntityLimiter {

  case class EntityTooLarge(limit: Int) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Int = 2*1024*1024 // 2 MB default

  def apply(service: HttpService, limit: Int = DefaultMaxEntitySize): HttpService =
    service.local { req: Request => req.copy(body = req.body |> takeBytes(limit)) }

  private def takeBytes(n: Int): Process1[ByteVector, ByteVector] = {
    def go(taken: Int, chunk: ByteVector): Process1[ByteVector, ByteVector] = {
      val sz = taken + chunk.length
      if (sz > n) fail(EntityTooLarge(n))
      else emit(chunk) ++ receive1(go(sz, _))
    }
    receive1(go(0,_))
  }

  def comsumeUpTo(n: Int): Process1[ByteVector, ByteVector] = {
    val p = process1.fold[ByteVector, ByteVector](ByteVector.empty) { (c1, c2) => c1 ++ c2 }
    takeBytes(n) |> p
  }

}
