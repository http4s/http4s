package org.http4s.internal.parboiled2

import org.specs2.specification.Scope
import org.http4s.internal.parboiled2.support.{ HList, HNil }

class ValueStackSpec extends TestParserSpec {

  "The ValueStack should properly support" >> {

    "push, size, toList" in new TestStack(stackSize = 3) {
      size === 0
      push(42)
      size === 1
      toList === List(42)
      push("yes")
      push(3.0)
      size === 3
      toList === List(42, "yes", 3.0)
      push("overflow") must throwA[ValueStackOverflowException]
    }

    "pushAll, toHList" in new TestStack(stackSize = 3) {
      pushAll(42 :: "yes" :: 4.5 :: HNil)
      size === 3
      toHList[HList]() === 42 :: "yes" :: 4.5 :: HNil
      pushAll("overflow" :: HNil) must throwA[ValueStackOverflowException]
      toHList[HList](start = -1) must throwAn[IllegalArgumentException]
      toHList[HList](start = 1, end = 0) must throwAn[IllegalArgumentException]
    }

    "insert" in new TestStack(stackSize = 4) {
      pushAll(1 :: 2 :: 3 :: HNil)
      insert(2, 1.5)
      toList === List(1, 1.5, 2, 3)
      insert(-1, 0) must throwAn[IllegalArgumentException]
      insert(2, 0) must throwA[ValueStackOverflowException]
      insert(5, 0) must throwA[ValueStackUnderflowException]
    }

    "pop" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: HNil)
      pop() === 3
      toList === List(1, 2)
      pop() === 2
      toList === List(1)
      pop() === 1
      isEmpty must beTrue
      pop() must throwA[ValueStackUnderflowException]
    }

    "pullOut" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: 4 :: HNil)
      pullOut(1) === 3
      toList === List(1, 2, 4)
      pullOut(2) === 1
      toList === List(2, 4)
      pullOut(2) must throwA[ValueStackUnderflowException]
      pullOut(-1) must throwAn[IllegalArgumentException]
    }

    "peek" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: HNil)
      peek === 3
      peek(1) === 2
      peek(2) === 1
      peek(3) must throwA[ValueStackUnderflowException]
      pullOut(-1) must throwAn[IllegalArgumentException]
    }

    "poke" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: HNil)
      poke(0, "3")
      toList === List(1, 2, "3")
      poke(1, "2")
      toList === List(1, "2", "3")
      poke(2, "1")
      toList === List("1", "2", "3")
      poke(3, 0) must throwA[ValueStackUnderflowException]
      poke(-1, 0) must throwAn[IllegalArgumentException]
    }

    "swap" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: HNil)
      swap()
      toList === List(1, 3, 2)
      pop()
      pop()
      swap() must throwA[ValueStackUnderflowException]
    }

    "swap3" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: HNil)
      swap3()
      toList === List(3, 2, 1)
      pop()
      swap3() must throwA[ValueStackUnderflowException]
    }

    "swap4" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: 4 :: HNil)
      swap4()
      toList === List(4, 3, 2, 1)
      pop()
      swap4() must throwA[ValueStackUnderflowException]
    }

    "swap5" in new TestStack(stackSize = 8) {
      pushAll(1 :: 2 :: 3 :: 4 :: 5 :: HNil)
      swap5()
      toList === List(5, 4, 3, 2, 1)
      pop()
      swap5() must throwA[ValueStackUnderflowException]
    }

  }

  class TestStack(stackSize: Int) extends ValueStack(stackSize, stackSize) with Scope
}
