/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.testing

import java.io.OutputStream

object NullOutStream extends OutputStream {
  override def write(b: Int): Unit = {
    //do nothing
  }
}
