/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

import org.http4s.Uri

object request extends RequestDsl {
  val Path: Uri.Path.type = Uri.Path
  val Root: Uri.Path.Root.type = Uri.Path.Root
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
