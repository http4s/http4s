package fix

import org.scalatest.FunSpecLike
import scalafix.testkit.AbstractSemanticRuleSuite

class RuleSuite extends AbstractSemanticRuleSuite with FunSpecLike {
  runAllTests()
}
