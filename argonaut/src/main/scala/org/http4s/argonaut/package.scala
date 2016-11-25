package org.http4s

import _root_.argonaut.PrettyParams

package object argonaut extends ArgonautInstances {
  protected def defaultPrettyParams: PrettyParams =
    PrettyParams.nospace
}
