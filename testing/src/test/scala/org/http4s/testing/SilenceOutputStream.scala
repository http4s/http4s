/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.testing

import org.specs2.specification.AroundEach
import org.specs2.execute.{AsResult, Result}
import org.http4s.testing.ErrorReporting._

/**
  * Wraps around each test and silences System.out and System.err output streams.
  * Restores back the original streams after each test case.
  */

trait SilenceOutputStream extends AroundEach {

  def around[R: AsResult](r: => R): Result =
    silenceOutputStreams {
      AsResult(r)
    }
}
