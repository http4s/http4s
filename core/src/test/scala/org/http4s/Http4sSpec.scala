package org.http4s

import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

/**
 * Common stack for http4s' own specs.
 */
trait Http4sSpec extends Specification with Http4s with ScalaCheck with TestInstances
