package org.http4s.zipkin.middleware

import org.http4s.HttpService
import org.http4s.zipkin.server.ServerRequirements

import scalaz.Reader

package object server {
  type ZipkinService = Reader[ServerRequirements, HttpService]

}
