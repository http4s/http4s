package com.example.http4s

import scalaz.concurrent.Task

package object blaze {
  /**
   * If you're using scalaz-7.1, you don't really want this.  Just
   * call `.run`.  This is a hack so that the primary examples in
   * src/main/scala can show undeprecated scalaz-7.2 usage.
   */
  implicit class Scalaz71CompatibilityOps[A](val self: Task[A]) {
    def unsafePerformSync: A =
      self.run
  }
}
