package org.http4s
package client

import java.io._
import java.util.concurrent.atomic._

import fs2._

object MockClient {
  def apply(service: HttpService, dispose: Task[Unit] = Task.now(())) = {
    val isShutdown = new AtomicBoolean(false)

    def interruptable(body: EntityBody, disposed: AtomicBoolean) = {
      def kill(reason: String, killed: AtomicBoolean): Stream[Task, Byte] = {
        if (killed.get)
          Stream.fail(new IOException(reason))
        else
          body
      }

      kill("response was disposed", disposed) ++
      kill("client was shut down", isShutdown)
    }

    def disposableService(service: HttpService) =
      Service.lift { req: Request =>
        val disposed = new AtomicBoolean(false)
        val req0 = req.copy(body = interruptable(req.body, disposed))
        service(req0) map { resp =>
          DisposableResponse(
            resp.copy(body = interruptable(resp.body, disposed)),
            Task.delay(disposed.set(true)).flatMap(_ => dispose)
          )
        }
      }

    Client(disposableService(service),
      Task.delay(isShutdown.set(true)))
  }
}
