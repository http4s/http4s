package com.example.http4s.zipkin

import org.http4s.zipkin.core.Endpoint

case class Config(
  endpoint: Endpoint, nextServiceName: String
)
