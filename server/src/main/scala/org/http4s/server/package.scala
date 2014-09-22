package org.http4s

import scalaz.concurrent.Task

package object server {
  type HttpService = Service[Request, Response]
}
