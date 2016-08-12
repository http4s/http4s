package org.http4s
package client

import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s.Status.ResponseClass.Successful
import scala.util.control.NoStackTrace

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
  def apply[A](f: Response => Task[A]): Task[A] = {
    val task = try f(response) catch { case e: Throwable => Task.fail(e) }
    task.onFinish { case _ => dispose }
  }
}

/**
  * A [[Client]] submits [[Request]]s to a server and processes the [[Response]].
  *
  * @param open a service to asynchronously return a [[DisposableResponse]] from
  *             a [[Request]].  This is a low-level operation intended for client
  *             implementations and middleware.
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

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request)(implicit d: EntityDecoder[A]): Task[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)):_*))
    } else req
    fetch(r) {
      case Successful(resp) =>
        d.decode(resp, strict = false).fold(throw _, identity)
      case failedResponse =>
        Task.fail(UnexpectedStatus(failedResponse.status))
    }
  }

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request)(implicit d: EntityDecoder[A]): Task[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)):_*))
    } else req
    fetch(r) { resp =>
      d.decode(resp, strict = false).fold(throw _, identity)
    }
  }

  @deprecated("Use expect", "0.14")
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

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response => Task[A]): Task[A] =
    Uri.fromString(s).fold(Task.fail, uri => get(uri)(f))

  /**
    * Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[A]): Task[A] =
    expect(Request(Method.GET, uri))(d)

  /**
    * Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[A]): Task[A] =
    Uri.fromString(s).fold(Task.fail, expect[A])

  /**
    * Submits a GET request and decodes the response.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  @deprecated("Use expect", "0.14")
  def getAs[A](uri: Uri)(implicit d: EntityDecoder[A]): Task[A] =
    fetchAs(Request(Method.GET, uri))(d)

  @deprecated("Use expect", "0.14")
  def getAs[A](s: String)(implicit d: EntityDecoder[A]): Task[A] =
    Uri.fromString(s).fold(Task.fail, uri => getAs[A](uri))

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

  def expect[A](req: Task[Request])(implicit d: EntityDecoder[A]): Task[A] =
    req.flatMap(expect(_)(d))

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  @deprecated("Use expect", "0.14")
  def fetchAs[A](req: Task[Request])(implicit d: EntityDecoder[A]): Task[A] =
    req.flatMap(fetchAs(_)(d))

  @deprecated("Use expect", "0.14")
  def prepAs[T](req: Task[Request])(implicit d: EntityDecoder[T]): Task[T] =
    fetchAs(req)(d)

  /** Shuts this client down, and blocks until complete. */
  def shutdownNow(): Unit =
    shutdown.run
}

final case class UnexpectedStatus(status: Status) extends RuntimeException with NoStackTrace {
  override def getMessage: String = s"unexpected HTTP status: $status"
}
