package org.http4s.netty.spdy

import org.http4s.{TrailerChunk, BodyChunk}
import scala.concurrent.Future

/**
* @author Bryce Anderson
*         Created on 12/10/13
*/
trait StreamOutput {

  /** Write data to the stream
    *
    * @param streamid ID of the stream to write to
    * @param chunk buffer of data to write
    * @return a future which will resolve once the data has made ot past the window
    */
  def writeStreamChunk(streamid: Int, chunk: BodyChunk, flush: Boolean): Future[Any]

  /** Write the end of a stream
    *
    * @param streamid ID of the stream to write to
    * @param chunk last body buffer
    * @param t optional Trailer
    * @return a future which will resolve once the data has made ot past the window
    */
  def writeStreamEnd(streamid: Int, chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any]

}
