package org.http4s.zipkin.core.interpreters.collector

import argonaut.Argonaut._
import org.http4s.zipkin.core.algebras.Collector
import org.http4s.zipkin.core.interpreters._
import org.http4s.zipkin.core.ZipkinInfo

import scalaz.concurrent.Task
import scalaz.~>

object Console extends Collector {
  override def send(zipkinInfo: ZipkinInfo): Unit = {
    Task.delay(println(zipkinInfo.asJson))
  }

}
