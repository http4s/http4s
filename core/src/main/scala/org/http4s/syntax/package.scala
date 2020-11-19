/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

package object syntax {
  object all extends AllSyntax
  object kleisli extends KleisliSyntax with KleisliSyntaxBinCompat0 with KleisliSyntaxBinCompat1
  object literals extends LiteralsSyntax
  @deprecated("Use cats.foldable._", "0.18.5")
  object nonEmptyList extends NonEmptyListSyntax
  object string extends StringSyntax
}
