package org.http4s

import scalaz.stream.Process
import scalaz.concurrent.Task
import Process._

/**
 * Created by Bryce Anderson on 3/30/14.
 */
package object websocket {

  case class Websocket(source: Process[Task, BodyChunk], sink: Sink[Task, BodyChunk])

  val websocketKey = AttributeKey.http4s[Websocket]("websocket")

  trait Decoder[D] {
    def decode(d: BodyChunk): D
  }


  def WS[E,D](source: Process[Task, E], sink: Sink[Task, D])(implicit w: SimpleWritable[E], d: Decoder[D]): Task[Response] = {
    val r = Response(
      status = Status.NotImplemented,
      attributes = AttributeMap(
        AttributeEntry(
          websocketKey,Websocket(source.map(w.asChunk(_)),
            sink.map(s => c => s(d.decode(c))))
        )
      )
    )

    Task.now(r)
  }

  /////////////////// Decoders ///////////////////////////////////////

  implicit val chunkDecoder = new Decoder[BodyChunk] {
    override def decode(d: BodyChunk): BodyChunk = d
  }

  implicit def stringDecoder(implicit charset: CharacterSet = CharacterSet.`UTF-8`) = new Decoder[String] {
    override def decode(d: BodyChunk): String = d.decodeString(charset)
  }

}
