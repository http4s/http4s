package org.http4s.zipkin.middleware

import org.http4s.HttpService

import scalaz.Reader

package object server {
  type ZipkinService = Reader[ServerRequirements, HttpService]

}
