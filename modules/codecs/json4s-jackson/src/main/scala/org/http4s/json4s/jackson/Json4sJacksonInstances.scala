/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package json4s
package jackson

import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods

trait Json4sJacksonInstances extends Json4sInstances[JValue] {
  override protected def jsonMethods: org.json4s.JsonMethods[JValue] = JsonMethods
}
