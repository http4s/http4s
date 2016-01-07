package org.http4s
package client

import org.http4s.headers.Accept

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.{eval, eval_}

/**
  * Contains a [[Response]] that needs to be disposed of to free the underlying
  * HTTP connection.
  * @param response
  * @param dispose
  */
final case class DisposableResponse(response: Response, dispose: Task[Unit]) {
  /**
    * Returns a task to handle the response, safely disposing of the underlying
    * HTTP connection when the task finishes.
    */
  def apply[A](f: Response => Task[A]): Task[A] =
    f(response).onFinish { case _ => dispose }
}

/**
  * A [[Client]] submits [[Request]]s to a server and processes the [[Response]].
  *
  * @param open a service to asynchronously return a [[DisposableResponse]] from
  *             a [[Request]].  This is a low-level operation intended for client
  *             implementations and middlewares.
  *
  * @param shutdown a Task to shut down this Shutdown this client, closing any
  *                 open connections and freeing resources
  */
final case class Client(open: Service[Request, DisposableResponse], shutdown: Task[Unit]) {
  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Request)(f: Response => Task[A]): Task[A] =
    open.run(req).flatMap(_.apply(f))

  /**
    * Returns this client as a [[Service]].  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to [[fetch]], and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toService[A](f: Response => Task[A]): Service[Request, A] =
    open.flatMapK(_.apply(f))

  /**
    * Returns this client as an [[HttpService]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  [[fetch]], [[fetchAs]],
    * [[toService]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpService: HttpService =
    open.map { case DisposableResponse(response, dispose) =>
      response.copy(body = response.body.onComplete(eval_(dispose)))
    }

  def streaming[A](req: Request)(f: Response => Process[Task, A]): Process[Task, A] =
    eval(open(req).map { case DisposableResponse(response, dispose) =>
      f(response).onComplete(eval_(dispose))
    }).flatMap(identity)

  @deprecated("Use toHttpService.run for compatibility, or fetch for safety", "0.12")
  def prepare(req: Request): Task[Response] =
    toHttpService.run(req)

  @deprecated("Use toHttpService.run for compatibility, or fetch for safety", "0.12")
  def apply(req: Request): Task[Response] =
    toHttpService.run(req)

  /**
    * Submits a request and decodes the response.  The underlying HTTP connection
    * is closed at the completion of the decoding.
    */
  def fetchAs[A](req: Request)(implicit d: EntityDecoder[A]): Task[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(m.head, m.tail:_*))
    } else req
    fetch(r) { resp =>
      d.decode(resp, strict = false).fold(e => throw DecodeFailureException(e), identity)
    }
  }

  @deprecated("Use fetchAs", "0.12")
  def prepAs[A](req: Request)(implicit d: EntityDecoder[A]): Task[A] =
    fetchAs(req)(d)

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response => Task[A]): Task[A] =
    fetch(Request(Method.GET, uri))(f)

  @deprecated("Use toHttpService.run(Request(Method.GET, uri)).run for compatibility, or get for safety", "0.12")
  def prepare(uri: Uri): Task[Response] =
    toHttpService.run(Request(Method.GET, uri))

  @deprecated("Use toHttpService.run(Request(Method.GET, uri)).run for compatibility, or get for safety", "0.12")
  def apply(uri: Uri): Task[Response] =
    toHttpService.run(Request(Method.GET, uri))

  /**
    * Submits a GET request and decodes the response.  The underlying HTTP connection
    * is closed at the completion of the decoding.
    */
  def getAs[A](uri: Uri)(implicit d: EntityDecoder[A]): Task[A] =
    fetchAs(Request(Method.GET, uri))(d)

  @deprecated("Use getAs", "0.12")
  def prepAs[A](uri: Uri)(implicit d: EntityDecoder[A]): Task[A] =
    getAs(uri)(d)

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req A Task of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Task[Request])(f: Response => Task[A]): Task[A] =
    req.flatMap(fetch(_)(f))

  @deprecated("Use toHttpService =<< req for compatibility, or fetch for safety", "0.12")
  def prepare(req: Task[Request]): Task[Response] =
    toHttpService =<< req

  @deprecated("Use toHttpService =<< req for compatibility, or fetch for safety", "0.12")
  def apply(req: Task[Request]): Task[Response] =
    toHttpService =<< req

  /**
    * Submits a request and decodes the response.  The underlying HTTP connection
    * is closed at the completion of the decoding.
    */
  def fetchAs[A](req: Task[Request])(implicit d: EntityDecoder[A]): Task[A] =
    req.flatMap(fetchAs(_)(d))

  @deprecated("Use fetchAs", "0.12")
  def prepAs[T](req: Task[Request])(implicit d: EntityDecoder[T]): Task[T] =
    fetchAs(req)(d)
}
