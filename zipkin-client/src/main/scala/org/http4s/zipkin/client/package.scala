package org.http4s.zipkin

import org.http4s.HttpService
import org.http4s.client.Client

import scalaz.Reader

package object client {
  type ServiceName = String
  type ZipkinClient = Reader[ClientRequirements, Client]
}
