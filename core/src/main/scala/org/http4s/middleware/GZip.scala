package org.http4s
package middleware

import scalaz.stream.Process._
import scalaz.concurrent.Task
import scodec.bits.ByteVector

import org.http4s.Header.{`Content-Type`, `Content-Length`, `Content-Encoding`, `Accept-Encoding`}
import util.Gzipper

import org.http4s.util.Logging

/**
 * @author Bryce Anderson
 *         Created on 11/30/13
 */
object GZip extends Logging {
  /** Streaming GZip Process1 */
  def streamingGZip(buffersize: Int): Process1[ByteVector, ByteVector] = {
    val gzip = new Gzipper(buffersize)

    def getBodyChunk = ByteVector(gzip.getBytes())

    val fb = emitLazy {
      gzip.finish()
      getBodyChunk
    }

    def folder(chunk: ByteVector): Process1[ByteVector, ByteVector] = {
      gzip.write(chunk.toArray)
      if (gzip.size() < 0.8*buffersize) await(Get[ByteVector])(folder, fb, fb) // Wait for ~80% buffer capacity
      else Emit(getBodyChunk::Nil, await(Get[ByteVector])(folder, fb ,fb))      // Emit a chunk
    }

    await(Get[ByteVector])(folder, fb, fb)
  }
  
  // TODO: It could be possible to look for Task.now type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply(route: HttpService, buffersize: Int = 512): HttpService = {

    new HttpService {
      override def isDefinedAt(x: Request): Boolean = route.isDefinedAt(x)

      override def apply(req: Request): Task[Response] = {
        //Header.`Accept-Encoding` req.prelude.headers
        val t = route(req)
        req.headers.get(`Accept-Encoding`).fold(t){ h =>
          if (h.satisfiedBy(ContentCoding.gzip) || h.satisfiedBy(ContentCoding.`x-gzip`)) t.map { resp =>
          // Accepts encoding. Make sure Content-Encoding is not set and transform body and add the header
            val contentType = resp.headers.get(`Content-Type`)
            if (resp.headers.get(`Content-Encoding`).isEmpty &&
              (contentType.isEmpty ||
                contentType.get.mediaType.compressible ||
                (contentType.get.mediaType eq MediaType.`application/octet-stream`))) {
              logger.trace("GZip middleware encoding content")
              val b = resp.body.pipe(streamingGZip(buffersize))
              resp.removeHeader(`Content-Length`)
                .addHeader(`Content-Encoding`(ContentCoding.gzip))
                .copy(body = b)
            }
            else resp  // Don't touch it, Content-Encoding already set
          } else t
        }
      }
    }
  }

}
