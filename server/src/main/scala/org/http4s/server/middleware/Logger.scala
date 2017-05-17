package org.http4s
package server
package middleware

import fs2.Strategy

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply(httpService: HttpService)(implicit strategy: Strategy): HttpService =
    ResponseLogger(RequestLogger(httpService))
}
