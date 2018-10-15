package org.http4s

import org.http4s.blaze.util.Cancelable

package object blazecore {
  private[blazecore] val NoOpCancelable = new Cancelable {
    def cancel() = ()
    override def toString = "no op cancelable"
  }
}
