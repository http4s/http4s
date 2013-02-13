package org.http4s

import scala.language.implicitConversions

import scala.io.Codec
import play.api.libs.iteratee.{Enumeratee, Enumerator}

trait Writable[-A] {
  def toChunk(a: A): HttpChunk
}

object Writable {
  def apply[A](f: A => Raw) = new Writable[A] { def toChunk(a: A) = HttpEntity(f(a)) }

  implicit def stringWritable(implicit codec: Codec) =
    Writable { s: String => s.getBytes(codec.charSet) }

  implicit def intWritable(implicit codec: Codec) =
    Writable { i: Int => i.toString.getBytes(codec.charSet) }

  implicit def chunkWritable = Writable { i: Raw => i }

  // It seems wasteful to wrap and unwrap sequences in HttpEntities
  implicit def seqWritable[A](implicit writable:Writable[A]) = Writable { i: Seq[A] =>
    i.map(writable.toChunk(_).bytes).flatten.toArray
  }
}

