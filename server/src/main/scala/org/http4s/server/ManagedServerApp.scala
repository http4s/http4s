package org.http4s
package server

import scalaz.{\/, Codensity, ImmutableArray}
import scalaz.concurrent.Task

import org.http4s.util.ManagedApp

trait ManagedServerApp extends ManagedApp {
  final def run(args: Vector[String]): Codensity[Task, Unit] =
    runServer(args).flatMap { server =>
      Codensity.rep(terminateServer(server))
    }

  /** Prompts for input on the console, and then terminates the server.
    * Override to implement your own termination condition.
    */
  def terminateServer(server: Server): Task[Unit] =
    Task.async { cb =>
      cb(\/.fromTryCatchNonFatal {
        Console.readLine(s"Press <Enter> to terminate server on ${server.address}\n")
      })
    }

  def runServer(args: Vector[String]): Codensity[Task, Server]
}
