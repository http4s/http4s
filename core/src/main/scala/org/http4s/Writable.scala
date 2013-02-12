package org.http4s

import scala.language.implicitConversions

import scala.io.Codec
import play.api.libs.iteratee._

trait Writable[-A] {
  def toChunk(a: A): Chunk
}

object Writable {
  def apply[A](f: A => Chunk) = new Writable[A] { def toChunk(a: A) = f(a) }

  implicit def stringWritable(implicit codec: Codec) =
    Writable { s: String => s.getBytes(codec.charSet) }

  implicit def intWritable(implicit codec: Codec) =
    Writable { i: Int => i.toString.getBytes(codec.charSet) }

  implicit def chunkWritable = Writable { i: Chunk => i }
}
