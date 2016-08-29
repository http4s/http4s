package org.http4s.zipkin.examples

import org.http4s.zipkin.models.Endpoint

case class Config(
  endpoint: Endpoint, nextServiceName: String
)
