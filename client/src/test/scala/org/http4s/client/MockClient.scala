package org.http4s
package client

import java.io._
import java.util.concurrent.atomic._
import fs2._

object MockClient {
  def apply(service: HttpService, dispose: Task[Unit] = Task.now(())): Client = {
    val isShutdown = new AtomicBoolean(false)

    def interruptable(body: EntityBody, disposed: AtomicBoolean): Stream[Task, Byte]  = {
      def killable[F[_]](reason: String, killed: AtomicBoolean): Pipe[F, Byte, Byte] = {
        def go(killed: AtomicBoolean): Handle[F, Byte] => Pull[F, Byte, Unit] = {
          _.receiveOption{
            case Some((chunk, h)) =>
              if (killed.get){
                Pull.outputs[F, Byte](Stream.fail[F](new IOException(reason)))
              } else {
                Pull.output[F, Byte](chunk.toBytes) >> go(killed)(h)
              }
            case None => Pull.done
          }
        }

        _.pull(go(killed))
      }
      body
        .through(killable("response was disposed", disposed))
        .through(killable("client was shut down", isShutdown))
    }

    def disposableService(service: HttpService): Service[Request, DisposableResponse] =
      Service.lift { req: Request =>
        val disposed = new AtomicBoolean(false)
        val req0 = req.withBody(interruptable(req.body, disposed))
        service(req0) map { resp =>
          DisposableResponse(
            resp.orNotFound.copy(body = interruptable(resp.orNotFound.body, disposed)),
            Task.delay(disposed.set(true)).flatMap(_ => dispose)
          )
        }
      }

    Client(disposableService(service),
      Task.delay(isShutdown.set(true)))
  }
}
