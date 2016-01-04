package org.http4s.client

import org.http4s._
import org.http4s.headers.Accept

import scalaz.concurrent.Task
import scalaz.stream.Process.eval_

trait Client {

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /**
    * Opens a disposable response.  The caller is responsible for running
    * the result's dispose task to free the underlying connection.  The response
    * body may not be read after calling dispose.
    *
    * This is a low-level method meant for implementors of clients and client
    * middleware.  It is encouraged that callers use [[fetch]], or if necessary,
    * [[stream]] instead.
    */
  def open(req: Request): Task[DisposableResponse]

  /**
    * Streams a response.  The caller is responsible for running the response
    * body in order to free the underlying connection.
    *
    * This is a low-level method.  It is encouraged that callers use [[fetch]]
    * instead.
    */
  final def stream(req: Request): Task[Response] =
    open(req).map { case DisposableResponse(resp, dispose) =>
      resp.copy(body = resp.body ++ eval_(dispose))
    }

  @deprecated("Use stream (equivalent) or fetch (safer)", "0.12")
  final def apply(req: Request): Task[Response] =
    stream(req)

  @deprecated("Use stream (equivalent) or fetch (safer)", "0.12")
  final def prepare(req: Request): Task[Response] =
    stream(req)

  /**
    * Fetches and handles a response asynchronously.  The underlying connection is
    * closed when the task returned by `f` completes, and no further reads from the
    * response body are permitted.
    */
  final def fetch[A](req: Request)(f: Response => Task[A]): Task[A] =
    open(req).flatMap { case DisposableResponse(resp, dispose) =>
      f(resp).onFinish { case _ => dispose }
    }

  /**
    * Fetches and decodes a response asynchronously.
    */
  final def fetchAs[A](req: Request)(implicit d: EntityDecoder[A]): Task[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(m.head, m.tail:_*))
    } else req
    fetch(r) { resp =>
      d.decode(resp, false).fold(e => throw DecodeFailureException(e), identity)
    }
  }

  @deprecated("Use fetchAs", "0.12")
  final def prepAs[T](req: Request)(implicit d: EntityDecoder[T]): Task[T] =
    fetchAs(req)(d)

  /**
    * Executes a GET request on the given uri.  The caller is responsible for
    * running the response body in order to free the underlying
    * connection.
    *
    * This is a low-level method.  It is encouraged that callers use [[get]]
    * instead.
    */
  final def getStream(uri: Uri): Task[Response] =
    stream(Request(Method.GET, uri = uri))

  @deprecated("Use getStream (equivalent) or get (safer)", "0.12")
  final def prepare(uri: Uri): Task[Response] =
    getStream(uri)

  @deprecated("Use getStream (equivalent) or get (safer)", "0.12")
  final def apply(uri: Uri): Task[Response] =
    getStream(uri)

  /**
    * Executes a GET request and handles the response asynchronously.  The underlying
    * connection is closed when the task returned by `f` completes, and no further
    * reads from the response body are permitted.
    */
  final def get[A](uri: Uri)(f: Response => Task[A]): Task[A] =
    fetch(Request(Method.GET, uri = uri))(f)

  /**
    * Executes a GET request and decodes the response.
    */
  final def getAs[A](uri: Uri)(implicit d: EntityDecoder[A]): Task[A] =
    fetchAs(Request(Method.GET, uri = uri))(d)

  @deprecated("Use getAs", "0.12")
  final def prepAs[A](uri: Uri)(implicit d: EntityDecoder[A]): Task[A] =
    getAs(uri)(d)

  /**
    * Streams a response.  The caller is responsible for running the response
    * body in order to free the underlying connection.
    *
    * This is a low-level method.  It is encouraged that callers use [[fetch]]
    * instead.
    */
  final def stream(req: Task[Request]): Task[Response] =
    req.flatMap(stream)

  @deprecated("Use stream (equivalent) or fetch (safer)", "0.12")
  final def prepare(req: Task[Request]): Task[Response] =
    stream(req)

  @deprecated("Use stream (equivalent) or fetch (safer)", "0.12")
  final def apply(req: Task[Request]): Task[Response] =
    stream(req)

  /**
    * Fetches and handles a response asynchronously.  The underlying connection is
    * closed when the task returned by `f` completes, and no further reads from the
    * response body are permitted.
    */
  final def fetch[A](req: Task[Request])(f: Response => Task[A]): Task[A] =
    req.flatMap(fetch(_)(f))

  /**
    * Fetches and decodes a response asynchronously.
    */
  final def fetchAs[A](req: Task[Request])(implicit d: EntityDecoder[A]): Task[A] =
    req.flatMap(fetchAs(_)(d))

  @deprecated("Use fetchAs", "0.12")
  final def prepAs[A](req: Task[Request])(implicit d: EntityDecoder[A]): Task[A] =
    fetchAs(req)(d)
}

case class DisposableResponse(response: Response, dispose: Task[Unit])
