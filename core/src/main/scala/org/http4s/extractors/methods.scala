package org.http4s
package extractors

trait MethodExtractor {
  protected[this] def method: Method

  def unapply[T](request: RequestPrelude): Option[RequestPrelude] =
    if (request.requestMethod.name.toUpperCase == method.name.toUpperCase) Some(request) else None
}

object Options extends MethodExtractor {
  protected[this] val method: Method = Method.Options
}

object Get extends MethodExtractor {
  protected[this] val method: Method = Method.Get
}

object Head extends MethodExtractor {
  protected[this] val method: Method = Method.Head
}

object Post extends MethodExtractor {
  protected[this] val method: Method = Method.Post
}

object Put extends MethodExtractor {
  protected[this] val method: Method = Method.Put
}

object Delete extends MethodExtractor {
  protected[this] val method: Method = Method.Delete
}

object Trace extends MethodExtractor {
  protected[this] val method: Method = Method.Trace
}

object Connect extends MethodExtractor {
  protected[this] val method: Method = Method.Connect
}

object Patch extends MethodExtractor {
  protected[this] val method: Method = Method.Patch
}

