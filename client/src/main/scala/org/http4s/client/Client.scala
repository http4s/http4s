package org.http4s.client

import com.typesafe.scalalogging.Logging
import org.http4s.client.Client.BadResponse
import org.http4s._

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task


trait Client { self: Logging =>

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  def prepare(req: Request): Task[Response]

  /** Prepare a single request
    * @param req [[Request]] containing the headers, URI, etc.
    * @return Task which will generate the Response
    */
  final def prepare(req: Task[Request]): Task[Response] = req.flatMap(prepare(_))

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  final def request[A](req: Task[Request], decoder: EntityDecoder[A]): Task[A] =
    req.flatMap(req => request(req, decoder))
  
  final def request[A](req: Request, decoder: EntityDecoder[A]): Task[A] = prepare(req).flatMap { resp =>
    if (resp.status == Status.Ok) {
      if (resp.contentType.isDefined && !decoder.matchesMediaType(resp)) {
        logger.warn(s"Response media type ${resp.contentType.get} " +
                    s"not recognized by decoder: ${decoder.consumes}")
      }
      decoder(resp)
    }
    else EntityDecoder.text(resp).flatMap(str => Task.fail(BadResponse(resp.status, str)))
  }
}

object Client {
  
  case class BadResponse(status: Status, msg: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Bad Response, $status: '$msg'"
  }
}
