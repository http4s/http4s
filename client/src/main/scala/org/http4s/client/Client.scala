package org.http4s
package client

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2._
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Status.Successful
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import scala.concurrent.SyncVar
import scala.util.control.NoStackTrace
import org.log4s.getLogger

/**
  * Contains a [[Response]] that needs to be disposed of to free the underlying
  * HTTP connection.
  * @param response
  * @param dispose
  */
final case class DisposableResponse[F[_]](response: Response[F], dispose: F[Unit]) {

  private[this] val logger = getLogger

  /**
    * Returns a task to handle the response, safely disposing of the underlying
    * HTTP connection when the task finishes.
    */
  def apply[A](f: Response[F] => F[A])(implicit F: MonadError[F, Throwable]): F[A] = {
    //Catch possible user bugs in pure expression
    val task: F[A] = try f(response)
    catch {
      case e: Throwable =>
        logger.error(e)("""Handled exception in client callback to prevent a connection leak.
             |The callback should always return an F. If your callback can fail
             |with an exception you can't handle, call `F.raiseError(exception)`.
          """.stripMargin)
        F.raiseError(e)
    }

    for {
      result <- task.attempt
      _ <- dispose
      fold <- result.fold[F[A]](F.raiseError, F.pure)
    } yield fold
  }
}

/**
  * A [[Client]] submits [[Request]]s to a server and processes the [[Response]].
  *
  * @param open a service to asynchronously return a [[DisposableResponse]] from
  *             a [[Request]].  This is a low-level operation intended for client
  *             implementations and middleware.
  *
  * @param shutdown an effect to shut down this Shutdown this client, closing any
  *                 open connections and freeing resources
  */
final case class Client[F[_]](
    open: Kleisli[F, Request[F], DisposableResponse[F]],
    shutdown: F[Unit])(implicit F: MonadError[F, Throwable]) {

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is disposed when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A] =
    open.run(req).flatMap(_.apply(f))

  /** Submits a request, and provides a callback to process the response.
    *
    * @param req An effect of the request to submit
    * @param f A callback for the response to req.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A] =
    req.flatMap(fetch(_)(f))

  /**
    * Returns this client as a [[Kleisli]].  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `fetch`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A] =
    open.flatMapF(_.apply(f))

  @deprecated("Use toKleisli", "0.18")
  def toService[A](f: Response[F] => F[A]): Service[F, Request[F], A] =
    toKleisli(f)

  /**
    * Returns this client as an [[HttpService]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `fetch`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpService: HttpService[F] =
    open
      .map {
        case DisposableResponse(response, dispose) =>
          response.copy(body = response.body.onFinalize(dispose))
      }
      .mapF(OptionT.liftF(_))

  def streaming[A](req: Request[F])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    Stream
      .eval(open(req))
      .flatMap {
        case DisposableResponse(response, dispose) =>
          f(response)
            .onFinalize(dispose)
      }

  def streaming[A](req: F[Request[F]])(f: Response[F] => Stream[F, A]): Stream[F, A] =
    Stream.eval(req).flatMap(streaming(_)(f))

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req
    fetch(r) {
      case Successful(resp) =>
        d.decode(resp, strict = false).fold(throw _, identity)
      case failedResponse =>
        F.raiseError(UnexpectedStatus(failedResponse.status))
    }
  }

  def expect[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    req.flatMap(expect(_)(d))

  /**
    * Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  def expect[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] =
    expect(Request[F](Method.GET, uri))(d)

  /**
    * Submits a GET request to the URI specified by the String and decodes the
    * response on success.  On failure, the status code is returned.  The
    * underlying HTTP connection is closed at the completion of the decoding.
    */
  def expect[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => expect[A](uri))

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req
    fetch(r) { resp =>
      d.decode(resp, strict = false).fold(throw _, identity)
    }
  }

  /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
    req.flatMap(fetchAs(_)(d))

  /** Submits a request and returns the response status */
  def status(req: Request[F]): F[Status] =
    fetch(req)(resp => F.pure(resp.status))

  /** Submits a request and returns the response status */
  def status(req: F[Request[F]]): F[Status] =
    req.flatMap(status)

  /** Submits a request and returns true if and only if the response status is
    * successful */
  def successful(req: Request[F]): F[Boolean] =
    status(req).map(_.isSuccess)

  /** Submits a request and returns true if and only if the response status is
    * successful */
  def successful(req: F[Request[F]]): F[Boolean] =
    req.flatMap(successful)

  @deprecated("Use expect", "0.14")
  def prepAs[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] =
    fetchAs(req)(d)

  /** Submits a GET request, and provides a callback to process the response.
    *
    * @param uri The URI to GET
    * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
    *          is disposed when the returned task completes.  Attempts to read the
    *          response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def get[A](uri: Uri)(f: Response[F] => F[A]): F[A] =
    fetch(Request[F](Method.GET, uri))(f)

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def get[A](s: String)(f: Response[F] => F[A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => get(uri)(f))

  /**
    * Submits a GET request and decodes the response.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
  @deprecated("Use expect", "0.14")
  def getAs[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] =
    fetchAs(Request[F](Method.GET, uri))(d)

  @deprecated("Use expect", "0.14")
  def getAs[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
    Uri.fromString(s).fold(F.raiseError, uri => expect[A](uri))

  @deprecated("Use expect", "0.14")
  def prepAs[T](req: F[Request[F]])(implicit d: EntityDecoder[F, T]): F[T] =
    fetchAs(req)

  /** Shuts this client down, and blocks until complete. */
  def shutdownNow()(implicit F: Effect[F]): Unit = {
    val wait = new SyncVar[Unit]
    F.runAsync(shutdown) { _ =>
        wait.put(())
        IO.unit
      }
      .unsafeRunSync()
    wait.get
  }
}

object Client {

  /** Creates a client from the specified service.  Useful for generating
    * pre-determined responses for requests in testing.
    *
    * @param service the service to respond to requests to this client
    */
  def fromHttpService[F[_]](service: HttpService[F])(implicit F: Sync[F]): Client[F] = {
    val isShutdown = new AtomicBoolean(false)

    def interruptible(body: EntityBody[F], disposed: AtomicBoolean): Stream[F, Byte] = {
      def killable(reason: String, killed: AtomicBoolean): Pipe[F, Byte, Byte] = {
        def go(killed: AtomicBoolean, stream: Stream[F, Byte]): Pull[F, Byte, Unit] =
          stream.pull.uncons.flatMap {
            case Some((segment, stream)) =>
              if (killed.get) {
                Pull.raiseError(new IOException(reason))
              } else {
                Pull.output(segment) >> go(killed, stream)
              }
            case None => Pull.done
          }

        stream =>
          go(killed, stream).stream
      }
      body
        .through(killable("response was disposed", disposed))
        .through(killable("client was shut down", isShutdown))
    }

    def disposableService(service: HttpService[F]): Kleisli[F, Request[F], DisposableResponse[F]] =
      Kleisli { req: Request[F] =>
        val disposed = new AtomicBoolean(false)
        val req0 = req.withBodyStream(interruptible(req.body, disposed))
        service(req0).getOrElse(Response.notFound).map { resp =>
          DisposableResponse(
            resp.copy(body = interruptible(resp.body, disposed)),
            F.delay(disposed.set(true))
          )
        }
      }

    Client(disposableService(service), F.delay(isShutdown.set(true)))
  }
}

final case class UnexpectedStatus(status: Status) extends RuntimeException with NoStackTrace {
  override def getMessage: String = s"unexpected HTTP status: $status"
}
