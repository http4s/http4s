package org.http4s

import org.specs2.matcher.{AnyMatchers, Matcher}
import org.specs2.scalaz.{Spec, ScalazMatchers}

/**
 * Common stack for http4s' own specs.
 */
trait Http4sSpec extends Spec with AnyMatchers with ScalazMatchers with Http4s with TestInstances
