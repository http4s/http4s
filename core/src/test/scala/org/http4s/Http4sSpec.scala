package org.http4s

import org.specs2.matcher.{AnyMatchers, OptionMatchers}
import org.specs2.scalaz.{Spec, ScalazMatchers}

import scalaz.std.AllInstances

/**
 * Common stack for http4s' own specs.
 */
trait Http4sSpec extends Spec with AnyMatchers with ScalazMatchers with OptionMatchers with Http4s with TestInstances with AllInstances
