package org.http4s
package cooldsl

import org.http4s.Headers
import scalaz.stream.Process
import scalaz.concurrent.Task
import scodec.bits.ByteVector

/**
 * Created by Bryce Anderson on 4/27/14.
 */
object BodyCodec {

  trait BodyTransformer[T]

  case class Decoder[T](codec: Dec[T]) extends BodyTransformer[T]

  case class OrDec[T](c1: Decoder[T], c2: Decoder[T]) extends BodyTransformer[T]

  trait Dec[T] {
    /** Check the headers to determine of this decoder is applicable */
    def checkHeaders(h: Headers): Boolean = true

    /** Decode the stream into a concrete T asynchronously */
    def decode(b: HttpBody): Task[T]
  }

  implicit def funcDecoder[T](f: HttpBody => Task[T]) = Decoder(funcDec(f))

  implicit def funcDec[T](f: HttpBody => Task[T]) = new Dec[T] {
    /** Decode the stream into a concrete T asynchronously */
    override def decode(b: HttpBody): Task[T] = f(b)
  }
}
