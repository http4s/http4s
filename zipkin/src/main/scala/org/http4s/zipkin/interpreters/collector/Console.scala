package org.http4s.zipkin.interpreters.collector

import argonaut.Argonaut._
import org.http4s.zipkin.algebras.CollectorOp
import org.http4s.zipkin.interpreters._

import scalaz.concurrent.Task
import scalaz.~>

object Console extends (CollectorOp ~> Task) {
  override def apply[A](fa: CollectorOp[A]): Task[A] = fa match {
    case CollectorOp.Send(zipkinInfo) =>
      Task.delay(println(zipkinInfo.asJson.spaces2))
  }

}
