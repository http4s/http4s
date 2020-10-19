/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

object request extends RequestDsl {
  val Path: impl.Path.type = impl.Path
  val Root: impl.Root.type = impl.Root
  val / : impl./.type = impl./
  val :? : impl.:?.type = impl.:?
  val ~ : impl.~.type = impl.~
  val -> : impl.->.type = impl.->
  val /: : impl./:.type = impl./:
  val +& : impl.+&.type = impl.+&

  val IntVar: impl.IntVar.type = impl.IntVar
  val LongVar: impl.LongVar.type = impl.LongVar
  val UUIDVar: impl.UUIDVar.type = impl.UUIDVar
}
