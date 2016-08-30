package org.http4s.zipkin.interpreters.collector

import argonaut.Argonaut._
import org.http4s.zipkin.algebras.Collector
import org.http4s.zipkin.interpreters._
import org.http4s.zipkin.models.ZipkinInfo

import scalaz.concurrent.Task
import scalaz.~>

object Console extends Collector {
  override def send(zipkinInfo: ZipkinInfo): Unit = {
    Task.delay(println(zipkinInfo.asJson.spaces2))
  }

}
