package org.http4s
package util.io

import java.io.{Writer => JWriter, _}
import org.http4s.util.byteVector._
import org.scalacheck._, Arbitrary._
import scalaz._
import scalaz.concurrent._
import scalaz.stream.text._
import scalaz.syntax.foldable._
import scodec.bits.ByteVector

class IoSpec extends Http4sSpec {
  implicit val s: Strategy = Strategy.DefaultStrategy

  case class ChunkOffsetLen(chunk: ByteVector, offset: Int, len: Int)

  implicit val arbitraryChunkOffsetLen = Arbitrary {
    for {
      chunk <- arbitrary[ByteVector]
      offset <- Gen.choose(0, chunk.size.toInt)
      len <- Gen.choose(0, chunk.size.toInt - offset)
    } yield ChunkOffsetLen(chunk, offset, len)
  }

  "captureOutputStream" should {
    "write bytes" in prop { chunks: Vector[ByteVector] =>
      val p = captureOutputStream(out => chunks.foreach(chunk => out.write(chunk.toArray))).runLog.unsafePerformSync
      p must_== chunks
    }

    "write(b) is consistent with write(b, 0, b.length)" in prop { chunk: ByteVector =>
      val p0 = captureOutputStream(out => out.write(chunk.toArray)).runLast.unsafePerformSync
      val p1 = captureOutputStream(out => out.write(chunk.toArray, 0, chunk.size.toInt)).runLast.unsafePerformSync
      p0 must_== p1
    }

    "write(b, offset, len) is consistent with writing individual bytes" in prop { clo: ChunkOffsetLen =>
      val p0 = captureOutputStream(out => out.write(clo.chunk.toArray, clo.offset, clo.len)).runLast.unsafePerformSync
      val p1 = captureOutputStream { out =>
        for (i <- clo.offset until (clo.offset + clo.len)) { out.write(clo.chunk(i.toLong).toInt) }
      }.foldMonoid.runLast.unsafePerformSync
      p0 must_== p1
    }

    "write eight low-order bits of an int" in prop { i: Int =>
      val p0 = captureOutputStream(out => out.write(i)).runLast.unsafePerformSync
      val p1 = captureOutputStream(out => out.write(i & 0xff)).runLast.unsafePerformSync
      p0 must_== p1
    }

    "work with bounded queues" in prop { (bound: Int, chunks: Vector[ByteVector]) =>
      (bound > 0) ==> {
        val f = { out: OutputStream => chunks.foreach(chunk => out.write(chunk.toArray)) }
        val p0 = captureOutputStream(f).runLog.unsafePerformSync
        val p1 = captureOutputStream(bound)(f).runLog.unsafePerformSync
        p0 must_== p1
      }
    }
  }

  "captureWriter" should {
    "write strings" in prop { chunks: Vector[String] =>
      val p = captureWriter(w => chunks.foreach(chunk => w.write(chunk)))
      p.pipe(utf8Decode).foldMonoid.runLast.unsafePerformSync must beSome(chunks.foldMap(identity))
    }

    "work with bounded queues" in prop { (bound: Int, chunks: Vector[String]) =>
      (bound > 0) ==> {
        val f = { w: JWriter => chunks.foreach(chunk => w.write(chunk)) }
        val p0 = captureWriter(f).runLog.unsafePerformSync
        val p1 = captureWriter(bound)(f).runLog.unsafePerformSync
        p0 must_== p1
      }
    }
  }
}
