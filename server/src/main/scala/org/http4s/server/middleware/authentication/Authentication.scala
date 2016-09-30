package org.http4s
package server
package middleware
package authentication

import scalaz.concurrent.Task
import scalaz._
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.{AuthedRequest, AuthedService}

/**
 * Authentication instances are middleware that provide a
 * {@link HttpService} with HTTP authentication.
 */
object Authentication {
}
