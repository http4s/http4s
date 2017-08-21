package org.http4s
package client

import java.io._
import java.util.concurrent.atomic._

import cats.effect._
import cats.implicits._
import fs2._

object MockClient {
  def apply[F[_]: Sync](service: HttpService[F]): Client[F] = apply(service, ().pure[F])

  def apply[F[_]](service: HttpService[F], dispose: F[Unit])
                 (implicit F: Sync[F]): Client[F] = {
    val isShutdown = new AtomicBoolean(false)

    def interruptable(body: EntityBody[F], disposed: AtomicBoolean): Stream[F, Byte]  = {
      def killable(reason: String, killed: AtomicBoolean): Pipe[F, Byte, Byte] = {
        def go(killed: AtomicBoolean, stream: Stream[F, Byte]): Pull[F, Byte, Unit] = {
          stream.pull.uncons.flatMap {
            case Some((segment, stream)) =>
              if (killed.get){
                Pull.fail(new IOException(reason))
              } else {
                Pull.output(segment) >> go(killed, stream)
              }
            case None => Pull.done
          }
        }

        stream => go(killed, stream).stream
      }
      body
        .through(killable("response was disposed", disposed))
        .through(killable("client was shut down", isShutdown))
    }

    def disposableService(service: HttpService[F]): Service[F, Request[F], DisposableResponse[F]] =
      Service.lift { req: Request[F] =>
        val disposed = new AtomicBoolean(false)
        val req0 = req.withBodyStream(interruptable(req.body, disposed))
        service(req0) map { resp =>
          DisposableResponse(
            resp.orNotFound.copy(body = interruptable(resp.orNotFound.body, disposed)),
            F.delay(disposed.set(true)).flatMap(_ => dispose)
          )
        }
      }

    Client(disposableService(service),
      F.delay(isShutdown.set(true)))
  }
}
