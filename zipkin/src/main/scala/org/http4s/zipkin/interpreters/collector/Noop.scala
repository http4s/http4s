package org.http4s.zipkin.interpreters.collector

import org.http4s.zipkin.algebras.{CollectorInterpreter, CollectorOp}

import scalaz.concurrent.Task

object Noop extends CollectorInterpreter {
  override def apply[A](fa: CollectorOp[A]): Task[A] = fa match {
    case CollectorOp.Send(zipkinInfo) =>
      Task.now(())
  }
}
