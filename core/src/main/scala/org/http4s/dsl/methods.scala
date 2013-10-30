package org.http4s
package dsl

abstract class MethodExtractor(val method: Method) {
  def unapply(request: RequestPrelude): Option[Path] =
    if (request.requestMethod == method) Some(Path(request.pathInfo) ) else None
}

object OPTIONS extends MethodExtractor(Method.Options)
object GET     extends MethodExtractor(Method.Get)
object HEAD    extends MethodExtractor(Method.Head)
object POST    extends MethodExtractor(Method.Post)
object PUT     extends MethodExtractor(Method.Put)
object DELETE  extends MethodExtractor(Method.Delete)
object TRACE   extends MethodExtractor(Method.Trace)
object CONNECT extends MethodExtractor(Method.Connect)
object PATCH   extends MethodExtractor(Method.Patch)

object ANY {
  def unapply(request: RequestPrelude): Option[Path] = Some(Path(request.pathInfo))
}
