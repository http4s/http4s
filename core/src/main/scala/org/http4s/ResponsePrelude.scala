package org.http4s

import scalaz.stream._
import scalaz.concurrent.Task
import java.net.{URL, URI}

case class ResponsePrelude(status: Status = Status.Ok, headers: HeaderCollection = HeaderCollection.empty)

