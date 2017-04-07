package org.http4s
package internal

private[http4s] object kestrel {
  implicit class KestrelOps[A](val self: A) extends AnyVal {
    def tap(f: A => Any): A = {
      f(self)
      self
    }
  }
}
