package org.http4s

import scala.language.implicitConversions

import scala.io.Codec
import play.api.libs.iteratee.{Enumeratee, Enumerator}

trait Writable[-A] {
  def toChunk(a: A): HttpEntity
}

object Writable {
  def apply[A](f: A => Raw) = new Writable[A] { def toChunk(a: A) = HttpEntity(f(a)) }

  implicit def stringWritable(implicit codec: Codec) =
    Writable { s: String => s.getBytes(codec.charSet) }

  implicit def intWritable(implicit codec: Codec) =
    Writable { i: Int => i.toString.getBytes(codec.charSet) }

  implicit def chunkWritable = Writable { i: Raw => i }
}

object Bodies {
  implicit def writableToBody[A](a: A)(implicit w: Writable[A]): Enumerator[HttpChunk] =
    Enumerator(w.toChunk(a))

  implicit def writableSeqToBody[A](a: Seq[A])(implicit w: Writable[A]): Enumerator[HttpChunk] =
    Enumerator(a.map { w.toChunk }: _*)

  implicit def writableEnumeratorToBody[A](a: Enumerator[A])(implicit w: Writable[A]): Enumerator[HttpChunk] =
    a &> Enumeratee.map(w.toChunk)
}