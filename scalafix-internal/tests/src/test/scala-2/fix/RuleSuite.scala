package fix

import org.scalatest.funspec.AnyFunSpecLike
import scalafix.testkit.AbstractSemanticRuleSuite

class RuleSuite extends AbstractSemanticRuleSuite with AnyFunSpecLike {
  runAllTests()
}
