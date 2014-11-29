package org.http4s.client

import org.http4s.client.Client.Result
import org.http4s.{InvalidResponseException, EntityDecoder, Status, Response}

import scalaz.concurrent.Task

trait ClientSyntaxBase {
  /** Generate a Task which, when executed, will perform the request using the provided client */
  protected def resp: Task[Response]

  /** Generate a Task which, when executed, will perform the request and if the response
    * is of type `status`, decodes it.
    */
  final def on[T](status: Status, s2: Status*)(decoder: EntityDecoder[T])(implicit client: Client): Task[Result[T]] = {
    decodeStatus { case s if (s == status || s2.contains(s)) => decoder }
  }


  /** Decode the [[Response]] based on [[Status]] */
  final def decodeStatus[T](f: PartialFunction[Status, EntityDecoder[T]]): Task[Result[T]] =
    decode { resp =>
      f.applyOrElse(resp.status, { s: Status =>
        EntityDecoder.error(InvalidResponseException(s"Unhandled Status: $s"))
      })
    }

  /** Generate a Task which, when executed, will perform the request and attempt to decode it */
  final def decode[T](f: Response => EntityDecoder[T]): Task[Result[T]] =
    Client.decode(resp)(f)

}
