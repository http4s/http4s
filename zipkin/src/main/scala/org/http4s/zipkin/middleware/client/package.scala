package org.http4s.zipkin.middleware

import org.http4s.client.Client

import scalaz.Reader

package object client {
  type ZipkinClient = Reader[ClientRequirements, Client]
  type ServiceName = String
}
