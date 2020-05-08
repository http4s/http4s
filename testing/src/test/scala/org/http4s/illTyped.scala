/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import java.util.regex.Pattern
import scala.reflect.macros.{ParseException, TypecheckException, whitebox}

/**
  * A utility which ensures that a code fragment does not typecheck.
  *
  * Credit: Stefan Zeiger (@StefanZeiger)
  */
object illTyped {
  def apply(code: String): Unit = macro IllTypedMacros.applyImplNoExp
  def apply(code: String, expected: String): Unit = macro IllTypedMacros.applyImpl
}

class IllTypedMacros(val c: whitebox.Context) {
  import c.universe._

  def applyImplNoExp(code: Tree): Tree = applyImpl(code, null)

  def applyImpl(code: Tree, expected: Tree): Tree = {
    val Literal(Constant(codeStr: String)) = code
    val (expPat, expMsg) = expected match {
      case null => (null, "Expected some error.")
      case Literal(Constant(s: String)) =>
        (
          Pattern.compile(s, Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
          "Expected error matching: " + s)
    }

    try {
      val dummy0 = TermName(c.freshName)
      val dummy1 = TermName(c.freshName)
      c.typecheck(c.parse(s"object $dummy0 { val $dummy1 = { $codeStr } }"))
      c.error(c.enclosingPosition, "Type-checking succeeded unexpectedly.\n" + expMsg)
    } catch {
      case e: TypecheckException =>
        val msg = e.getMessage
        if ((expected ne null) && !(expPat.matcher(msg)).matches)
          c.error(
            c.enclosingPosition,
            "Type-checking failed in an unexpected way.\n" + expMsg + "\nActual error: " + msg)
      case e: ParseException =>
        c.error(c.enclosingPosition, s"Parsing failed.\n${e.getMessage}")
    }

    q"()"
  }
}
