/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.json4s
package jackson

import org.json4s.JsonAST.JValue

class Json4sJacksonSpec extends Json4sSuite[JValue] with Json4sJacksonInstances
