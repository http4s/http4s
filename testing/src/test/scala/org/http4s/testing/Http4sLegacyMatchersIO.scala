/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package testing

import cats.effect.IO
import org.specs2.matcher.{IOMatchers => Specs2IOMatchers}

trait Http4sLegacyMatchersIO extends Http4sLegacyMatchers[IO] with Specs2IOMatchers
