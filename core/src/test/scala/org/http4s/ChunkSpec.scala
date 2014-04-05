package org.http4s

import org.scalatest.{Matchers, WordSpec}

/**
 * @author Bryce Anderson
 *         Created on 12/21/13
 */
class ChunkSpec extends WordSpec with Matchers {

  "BodyChunk" should {

    val data1 = Array[Byte](1, 2, 3)
    val data2 = Array[Byte](4, 5, 6)
    val data = data1 ++ data2

    def compareBodyChunk(c: BodyChunk, a: Array[Byte]) {
      c.toArray should equal(a)
      c.length should equal(a.length)
    }

    val e = BodyChunk()
    val c1 = BodyChunk(data1)
    val c2 = BodyChunk(data2)
    val c = c1 ++ c2

    "Construct an empty BodyChunk" in {
      compareBodyChunk(e, Array.empty)
    }

    "Build from array" in {
      compareBodyChunk(c1, data1)
    }

    "Concat to empty array properly" in {
      val c = e ++ c1
      compareBodyChunk(c, data1)
      val c2 = c1 ++ e
      compareBodyChunk(c2, data1)
    }

    "Concat to non-empty BodyChunk" in {
      compareBodyChunk(c, data)
    }

    "Copy to a short array" in {
      var a = new Array[Byte](3)
      c.copyToArray(a)
      a should equal(data1)

      a = new Array[Byte](2)
      c.copyToArray(a)
      a should equal(data.slice(0, 2))

      a = new Array[Byte](2)
      c.copyToArray(a, 1)
      a should equal(new Array[Byte](1) ++ data.slice(0, 1))

      a = new Array[Byte](3)
      c.copyToArray(a, 1, 1)
      a should equal(new Array[Byte](1) ++ data.slice(0, 1) ++ new Array[Byte](1))

      a = new Array[Byte](3)
      c.copyToArray(a, 1, 10)
      a should equal(new Array[Byte](1) ++ data.slice(0, 2))
    }

    "Copy to a long array" in {
      var a = new Array[Byte](10)
      c.copyToArray(a)
      a should equal(data ++ new Array[Byte](10 - data.length))

      a = new Array[Byte](10)
      c.copyToArray(a, a.length - c.length)
      a should equal(new Array[Byte](10 - data.length) ++ data)

      a = new Array[Byte](10)
      c.copyToArray(a, 0, 3)
      a should equal(data.slice(0,3) ++ new Array[Byte](a.length - 3))

      a = new Array[Byte](10)
      c.copyToArray(a, 4, 3)
      a should equal(new Array[Byte](4) ++ data.slice(0, 3) ++ new Array[Byte](3))

      a = new Array[Byte](10)
      c.copyToArray(a, 7, 30)
      a should equal(new Array[Byte](7) ++ data.slice(0, 3))
    }

    "split properly" in {
      val dd = data ++ data
      val d = c ++ c
      val pairs = Seq((dd, d), (data, c), (data1, c1), (data2, c2))
      pairs.foreach { case (bytes, chunk) =>
        -10 until bytes.length + 10 foreach { i =>
          val (a,b) = chunk.splitAt(i)
          compareBodyChunk(a, bytes.slice(0, i))
          compareBodyChunk(b, bytes.slice(i, d.length))
        }
      }
    }

  }

  "TrailerChunk" should {
    val e = TrailerChunk()
    val h1 = Header.`Content-Length`(1)
    val h5 = Header.`Content-Length`(5)
    val c1 = TrailerChunk(HeaderCollection(h1))
    val c5 = TrailerChunk(HeaderCollection(h5))

    "Have zero length" in {
      e.length should equal(0)
      e.toArray should equal(Array.empty[Byte])
      e.headers.length should equal(0)
    }

    "Hold headers" in {
      c1.headers.length should equal(1)
      c1.headers.iterator.next() should equal(h1)
    }

    "Combine properly" in {
      val c = c1 ++ c5
      c.length should equal(0)
      c.headers.length should equal(1)
      c.headers.get(Header.`Content-Length`).get.length should equal(h5.length)
    }
  }

}
