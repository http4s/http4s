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
import scala.util.control.NoStackTrace
import org.log4s.getLogger
/**
  * A [[Client]] submits [[Request]]s to a server and processes the [[Response]].
  *
  */
trait Client[F[_]]{
  import Client._

  /**
    * The fundamental unit of a Client that Streams a Response
    * From a Request
    */ 
  def kleisli: Kleisli[Stream[F, ?], Request[F], Response[F]]
  
  def streaming(req: Request[F]): Stream[F, Response[F]] =
    kleisli(req)


  /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is disposed when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
  def fetch[A](req: Request[F])(f: Response[F] => F[A])(implicit F: Sync[F]): F[A] =
    streaming(req)
      .evalMap(f)
      .compile
      .last
      .flatMap{
        _.fold(Sync[F].raiseError[A](SingletonStreamEmpty("fetch")))(_.pure[F])
      }

    /**
    * Submits a request and decodes the response, regardless of the status code.
    * The underlying HTTP connection is closed at the completion of the
    * decoding.
    */
  def fetchAs[A](req: Request[F])(implicit d: EntityDecoder[F, A], F: Sync[F]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req
    fetch(r) { resp =>
      d.decode(resp, strict = false).fold(throw _, identity)
    }
  }

    /**
    * Returns this client as a [[Kleisli]] in F.  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `fetch`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
  def toKleisli[A](f: Response[F] => F[A])(implicit F: Sync[F]): Kleisli[F, Request[F], A] = 
    Kleisli{ req: Request[F] => fetch(req)(f) }
  
    /**
    * Returns this client as an [[HttpService]].  It is the responsibility of
    * callers of this service to run the response body to dispose of the
    * underlying HTTP connection.
    *
    * This is intended for use in proxy servers.  `fetch`, `fetchAs`,
    * [[toKleisli]], and [[streaming]] are safer alternatives, as their
    * signatures guarantee disposal of the HTTP connection.
    */
  def toHttpService(implicit F: Sync[F]): HttpService[F] =
    Kleisli{req: Request[F] => OptionT.liftF(fetch(req)(_.pure[F]))}
  

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A], F: Sync[F]): F[A] = {
    val r = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
    } else req
    fetch(r) {
      case Successful(resp) =>
        d.decode(resp, strict = false).value.flatMap{ _.fold(F.raiseError[A], _.pure[F]) }
      case failedResponse =>
        F.raiseError(UnexpectedStatus(failedResponse.status))
    }
  }
  
  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def getUriAs[A](uri: Uri)(implicit d: EntityDecoder[F, A], F: Sync[F]): F[A] =
    fetchAs(Request[F](Method.GET, uri))

  /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
  def getStringAs[A](s: String)(implicit d: EntityDecoder[F, A], F: Sync[F]): F[A] = 
    Uri.fromString(s).fold(F.raiseError, uri => getUriAs(uri))

  /** Submits a request and returns the response status */
  def status(req: Request[F])(implicit F: Sync[F]): F[Status] = 
    fetch(req)(resp => F.pure(resp.status))
  
  /** 
    * Submits a request and returns true if and only if the response status is
    * successful 
    */
  def successful(req: Request[F])(implicit F: Sync[F]): F[Boolean] =
    status(req).map(_.isSuccess)
}

object Client {

  def apply[F[_]](f: Kleisli[Stream[F, ?], Request[F], Response[F]]): Client[F] = 
    new Client[F]{
      override def kleisli = f
    }

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

      def kleisliFakeClient: Kleisli[Stream[F, ?], Request[F], Response[F]] = Kleisli{req: Request[F] => 
        Stream.bracket(disposableService(service)(req))(dr => Stream(dr.response).covary[F], _.dispose)
      }

    new Client[F]{
      def kleisli = kleisliFakeClient
    }
  }

  /**
  * Contains a [[Response]] that needs to be disposed of to free the underlying
  * HTTP connection.
  * @param response
  * @param dispose
  */
  private case class DisposableResponse[F[_]](response: Response[F], dispose: F[Unit]) {

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

  final case class UnexpectedStatus(status: Status) extends RuntimeException with NoStackTrace {
    override def getMessage: String = s"unexpected HTTP status: $status"
  }
  final case class SingletonStreamEmpty(function: String) extends RuntimeException {
    override def getMessage: String = 
    s"Singleton Stream Empty - Invalid State: If this ever appears it is a BUG in Http4s - Function $function"
  }
}


