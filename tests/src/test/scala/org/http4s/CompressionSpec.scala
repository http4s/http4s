package org.http4s

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.{Deflater, GZIPOutputStream}

import cats.effect.IO
import fs2.{Chunk, Stream}
import org.http4s.headers.`Accept-Encoding`
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

class CompressionSpec extends Http4sSpec {

  "Compression" should {
    checkAll(
      "decode GZIP encoding",
      new Properties("GZip") {
        property("middleware decoding == GZIPOutputStream encoding") = forAll {
          body: String =>
            val entity = EntityEncoder[IO, String].toEntity(body).unsafeRunSync().body
            val r = Response[IO](body = entity)
            val bufferSize = 32 * 1024
            val zipped =
              Compression.zipResponse[IO](bufferSize, level = Deflater.DEFAULT_COMPRESSION, r)
            val unzipped = Compression
              .unzipResponse(bufferSize, zipped)

            EntityDecoder
              .text[IO]
              .decode(unzipped, strict = false) must returnRight(body)
        }
      }
    )
  }
}
