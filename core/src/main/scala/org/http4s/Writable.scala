package org.http4s

import io.Codec

trait Writable[-A] {
  def toBytes(a: A): Array[Byte]
}

object Writable {
  def apply[A](f: A => Array[Byte]) = new Writable[A] { def toBytes(a: A) = f(a) }

  implicit def stringWritable(implicit codec: Codec) = apply { s: String => s.getBytes(codec.charSet) }

  implicit def intWriteable(implicit codec: Codec) = apply { i: Int => i.toString.getBytes(codec.charSet) }
}
