package org.http4s

trait Http4s extends Http4sInstances with Http4sFunctions with syntax.AllSyntax

object Http4s extends Http4s

trait Http4sInstances
    extends EntityDecoderInstances
    with HttpVersionInstances
    with EntityEncoderInstances
    with CharsetRangeInstances
    with QValueInstances
    with MethodInstances
    with StatusInstances
    with MaybeResponseInstances

object Http4sInstances extends Http4sInstances

trait Http4sFunctions extends QValueFunctions with UriFunctions

object Http4sFunctions extends Http4sFunctions
