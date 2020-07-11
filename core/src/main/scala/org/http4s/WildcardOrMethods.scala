/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import org.http4s.util.{Renderable, Writer}
import cats.data.NonEmptyList
import cats.syntax.foldable._

sealed trait WildcardOrMethods extends Renderable

final case class Wildcard() extends WildcardOrMethods {

  override def render(writer: Writer): writer.type = {
    writer << '*'
    writer
  }
}

final case class Methods(methods: NonEmptyList[Method]) extends WildcardOrMethods {
  override def render(writer: Writer): writer.type = {
    writer.append(methods.mkString_(","))
    writer
  }
}
