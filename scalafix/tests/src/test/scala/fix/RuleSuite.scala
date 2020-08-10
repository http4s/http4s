/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package fix

import org.scalatest.FunSpecLike
import scalafix.testkit.AbstractSemanticRuleSuite

class RuleSuite extends AbstractSemanticRuleSuite with FunSpecLike {
  runAllTests()
}
