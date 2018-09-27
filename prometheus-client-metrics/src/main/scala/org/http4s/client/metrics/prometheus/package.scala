package org.http4s.client.metrics

/**
  * This package contains an implementation of [[org.http4s.client.metrics.core.Metrics]] middleware capable of
  * recording Prometheus metrics
  *
  * For example to following code would wrap a [[org.http4s.client.Client]] with a [[org.http4s.client.metrics.core.Metrics]]
  * that records metrics to a given Metric Registry: registry classifying requests by HTTP method.
  * {{{
  * import org.http4s.client.metrics.core.Metrics
  * import org.http4s.client.metrics.prometheus._
  *
  * val requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)
  * val meteredClient = Metrics(registry, "prefix", requestMethodClassifier)(client)
  * }}}
  */
package object prometheus extends PrometheusOpsFactoryInstances
