package org.http4s
package client

import java.io._
import java.util.concurrent.atomic._

import scalaz.concurrent._
import scalaz.stream._, Process._
import scodec.bits.ByteVector

object MockClient {
  def apply(service: HttpService, dispose: Task[Unit] = Task.now(())) = {
    val isShutdown = new AtomicBoolean(false)

    def interruptable(body: EntityBody, disposed: AtomicBoolean) = {
      def loop(reason: String, killed: AtomicBoolean): Process1[ByteVector, ByteVector] = {
        if (killed.get)
          fail(new IOException(reason))
        else
          await1[ByteVector] ++ loop(reason, killed)
      }
      body.pipe(loop("response was disposed", disposed))
        .pipe(loop("client was shut down", isShutdown))
    }

    def disposableService(service: HttpService) =
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
