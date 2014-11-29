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
  final def on[T](status: Status)(decoder: EntityDecoder[T])(implicit client: Client): Task[Result[T]] =
    decodeStatus { s =>
      if (s == status) decoder
      else EntityDecoder.error(InvalidResponseException(s"Wrong Status: $s"))
    }

  /** Decode the [[Response]] based on [[Status]] */
  final def decodeStatus[T](f: Status => EntityDecoder[T]): Task[Result[T]] =
    decode(resp => f(resp.status))

  /** Generate a Task which, when executed, will perform the request and attempt to decode it */
  final def decode[T](f: Response => EntityDecoder[T]): Task[Result[T]] =
    Client.decode(resp)(f)
}