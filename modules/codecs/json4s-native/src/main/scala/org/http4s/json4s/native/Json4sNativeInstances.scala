/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package json4s
package native

import org.json4s.native.{Document, JsonMethods}

trait Json4sNativeInstances extends Json4sInstances[Document] {
  override protected def jsonMethods: org.json4s.JsonMethods[Document] = JsonMethods
}
