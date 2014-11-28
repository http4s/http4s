package org.http4s
package json
package jawn

import scalaz.EitherT
import scalaz.concurrent.Task
import _root_.jawn.Facade
import jawnstreamz.JsonSourceSyntax

trait JawnDecodeSupport[J] extends JsonDecodeSupport[J] {
  protected implicit def jawnFacade: Facade[J]
}
