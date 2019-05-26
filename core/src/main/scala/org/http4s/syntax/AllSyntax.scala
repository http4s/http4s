package org.http4s
package syntax

abstract class AllSyntaxBinCompat extends AllSyntax with KleisliSyntaxBinCompat0 with KleisliSyntaxBinCompat1

trait AllSyntax
    extends AnyRef
    with AsyncSyntax
    with KleisliSyntax
    with NonEmptyListSyntax
    with StringSyntax
    with LiteralsSyntax
