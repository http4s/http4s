/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package syntax

abstract class AllSyntaxBinCompat
    extends AllSyntax
    with KleisliSyntaxBinCompat0
    with KleisliSyntaxBinCompat1

trait AllSyntax
    extends AnyRef
    with KleisliSyntax
    with NonEmptyListSyntax
    with StringSyntax
    with LiteralsSyntax
