package org.http4s
package client

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.Status.Successful
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import scala.util.control.NoStackTrace

object Client {

  object Ops extends ClientSyntax

  trait ClientSyntax {
    implicit class ClientOps[F[_] : Sync](k: Kleisli[Stream[F, ?], Request[F], Response[F]]){
    
    def streaming[A](req: Request[F]): Stream[F, Response[F]] =
      k(req)

    /** Submits a request, and provides a callback to process the response.
    *
    * @param req The request to submit
    * @param f   A callback for the response to req.  The underlying HTTP connection
    *            is disposed when the returned task completes.  Attempts to read the
    *            response body afterward will result in an error.
    * @return The result of applying f to the response to req
    */
    def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A] = 
      streaming(req)
      .evalMap(f)
      .compile
      .last
      .flatMap{
        _.fold(Sync[F].raiseError[A](SingletonStreamEmpty("fetch")))(_.pure[F])
      }


    /**
    * Returns this client as a [[Kleisli]].  All connections created by this
    * service are disposed on completion of callback task f.
    *
    * This method effectively reverses the arguments to `fetch`, and is
    * preferred when an HTTP client is composed into a larger Kleisli function,
    * or when a common response callback is used by many call sites.
    */
    def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A] =
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
    def toHttpService: HttpService[F] = 
      Kleisli{req: Request[F] => OptionT.liftF(fetch(req)(_.pure[F]))}

    def expectOr[A](req: Request[F])
                    (onError: Response[F] => F[Throwable])
                    (implicit d: EntityDecoder[F, A]): F[A] = {
      val r = if (d.consumes.nonEmpty) {
        val m = d.consumes.toList
        req.putHeaders(Accept(MediaRangeAndQValue(m.head), m.tail.map(MediaRangeAndQValue(_)): _*))
      } else req
      fetch(r) {
        case Successful(resp) =>
          d.decode(resp, strict = false).fold(throw _, identity)
        case failedResponse =>
          onError(failedResponse).flatMap(Sync[F].raiseError)
      }
    }

    def expectOrUri[A](uri: Uri)
                    (onError: Response[F] => F[Throwable])
                    (implicit d: EntityDecoder[F, A]): F[A] =
      expectOr(Request[F](Method.GET, uri))(onError)
    
    def expectOrString[A](s: String)
                    (onError: Response[F] => F[Throwable])
                    (implicit d: EntityDecoder[F, A]): F[A] =
      Uri.fromString(s).fold(Sync[F].raiseError, uri => expectOrUri[A](uri)(onError))

    /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
    def expect[A](req: Request[F])(implicit d: EntityDecoder[F, A]): F[A] = 
      expectOr(req)(Client.DefaultOnError[F])

    /**
    * Submits a GET request to the specified URI and decodes the response on
    * success.  On failure, the status code is returned.  The underlying HTTP
    * connection is closed at the completion of the decoding.
    */
    def expectUri[A](uri: Uri)(implicit d: EntityDecoder[F, A]): F[A] = 
      expectOrUri(uri)(Client.DefaultOnError[F])

   /**
   * Submits a GET request to the URI specified by the String and decodes the
   * response on success.  On failure, the status code is returned.  The
   * underlying HTTP connection is closed at the completion of the decoding.
   */
    def expectString[A](s: String)(implicit d: EntityDecoder[F, A]): F[A] =
      expectOrString(s)(Client.DefaultOnError[F])


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

    /** Submits a request and returns the response status */
    def status(req: Request[F]): F[Status] = 
      fetch(req)(resp => Applicative[F].pure(resp.status))

   /** 
   * Submits a request and returns true if and only if the response status is
   * successful 
   */
    def successful(req: Request[F]): F[Boolean] =
      status(req).map(_.isSuccess)

   /** Submits a GET request, and provides a callback to process the response.
   *
   * @param uri The URI to GET
   * @param f A callback for the response to a GET on uri.  The underlying HTTP connection
   *          is disposed when the returned task completes.  Attempts to read the
   *          response body afterward will result in an error.
   * @return The result of applying f to the response to req
   */
    def getUri[A](uri: Uri)(f: Response[F] => F[A]): F[A] =
      fetch(Request[F](Method.GET, uri))(f)
    
    /**
    * Submits a request and decodes the response on success.  On failure, the
    * status code is returned.  The underlying HTTP connection is closed at the
    * completion of the decoding.
    */
    def getString[A](s: String)(f: Response[F] => F[A]): F[A] =
      Uri.fromString(s).fold(Sync[F].raiseError, uri => getUri(uri)(f))

    }
  }


  def fromHttpService[F[_]: Sync](service: HttpService[F]): Client[F] = {
    val k = Kleisli[Stream[F, ?], Request[F], Response[F]]{req0 => 
      Stream.eval(service(req0).getOrElse(Response.notFound))
    }
    k
  }

  private def DefaultOnError[F[_]](resp: Response[F])(implicit F: Applicative[F]): F[Throwable] =
    Applicative[F].pure(UnexpectedStatus(resp.status))

  final case class SingletonStreamEmpty(function: String) extends RuntimeException {
    override def getMessage: String = 
    s"Singleton Stream Empty - Invalid State: If this ever appears it is a BUG in Http4s - Function $function"
  }

  final case class UnexpectedStatus(status: Status) extends RuntimeException with NoStackTrace {
    override def getMessage: String = s"unexpected HTTP status: $status"
  }

}