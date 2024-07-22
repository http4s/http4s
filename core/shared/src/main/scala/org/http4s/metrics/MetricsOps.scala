/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.metrics

import cats.Foldable
import cats.~>
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.SocketAddress
import org.http4s.HttpVersion
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

/** Describes an algebra capable of writing metrics to a metrics registry.
  *
  * The algebra provides enough information to fill out all required and
  * optional [[https://opentelemetry.io/docs/specs/semconv/http/http-metrics OpenTelemetry attributes]].
  */
trait MetricsOps[F[_]] {

  /** Increases the count of active requests
    *
    * {{{
    * | Name                | Example                    | Requirement level |
    * |---------------------|----------------------------|-------------------|
    * | http.request.method | `GET`, `POST`              |     Required      |
    * | url.scheme          | `http`, `https`            |     Required      |
    * | server.address      | `example.com`, `10.1.2.80` |     Opt-In        |
    * | server.port         | `80`, `8080`               |     Opt-In        |
    * }}}
    *
    * @param method the http method of the request
    * @param uri the URI of the request
    * @param address the address of the local server
    * @param classifier the classifier to apply
    */
  def increaseActiveRequests(
      method: Method,
      uri: Uri,
      address: Option[SocketAddress[IpAddress]],
      classifier: Option[String],
  ): F[Unit]

  /** Decreases the count of active requests
    *
    * {{{
    * | Name                | Example                    | Requirement level |
    * |---------------------|----------------------------|-------------------|
    * | http.request.method | `GET`, `POST`              |     Required      |
    * | url.scheme          | `http`, `https`            |     Required      |
    * | server.address      | `example.com`, `10.1.2.80` |     Opt-In        |
    * | server.port         | `80`, `8080`               |     Opt-In        |
    * }}}
    *
    * @param method the http method of the request
    * @param uri the URI of the request
    * @param address the address of the local server
    * @param classifier the classifier to apply
    */
  def decreaseActiveRequests(
      method: Method,
      uri: Uri,
      address: Option[SocketAddress[IpAddress]],
      classifier: Option[String],
  ): F[Unit]

  /** Records the time to receive the response headers
    *
    * @param method the http method of the request
    * @param uri the URI of the request
    * @param protocol the protocol of the request
    * @param address the address of the local server
    * @param elapsed the headers receiving time
    * @param classifier the classifier to apply
    */
  def recordHeadersTime(
      method: Method,
      uri: Uri,
      protocol: NetworkProtocol,
      address: Option[SocketAddress[IpAddress]],
      elapsed: FiniteDuration,
      classifier: Option[String],
  ): F[Unit]

  /** Records the time to fully consume the response, including the body
    *
    * {{{
    * | Name                      | Example                         | Requirement level                                            |
    * |---------------------------|---------------------------------|--------------------------------------------------------------|
    * | http.request.method       | `GET`, `POST`                   | Required                                                     |
    * | url.scheme                |  `http`, `https`                | Required                                                     |
    * | error.type                | `java.net.UnknownHostException` | Required If request has ended with an error                  |
    * | http.response.status_code | `200`, `404`                    | Conditionally Required If and only if one was received/sent. |
    * | network.protocol.name     |  `http`, `spdy`                 | Conditionally Required                                       |
    * | network.protocol.version  | `1.0`, `1.1`, `2`, `3`          | Recommended                                                  |
    * | server.address            | `example.com`, `10.1.2.80`      | Opt-In                                                       |
    * | server.port               | `80`, `8080`                    | Opt-In                                                       |
    * }}}
    *
    * @param method the http method of the request
    * @param uri the URI of the request
    * @param protocol the protocol of the request
    * @param address the address of the local server
    * @param status the status of the response
    * @param terminationType the termination type
    * @param elapsed the processing time
    * @param classifier the classifier to apply
    */
  def recordTotalTime(
      method: Method,
      uri: Uri,
      protocol: NetworkProtocol,
      address: Option[SocketAddress[IpAddress]],
      status: Option[Status],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      classifier: Option[String],
  ): F[Unit]

  /** Records the size of the request body
    *
    * {{{
    * | Name                      | Example                         | Requirement level                                            |
    * |---------------------------|---------------------------------|--------------------------------------------------------------|
    * | http.request.method       | `GET`, `POST`                   | Required                                                     |
    * | url.scheme                |  `http`, `https`                | Required                                                     |
    * | error.type                | `java.net.UnknownHostException` | Required If request has ended with an error                  |
    * | http.response.status_code | `200`, `404`                    | Conditionally Required If and only if one was received/sent. |
    * | network.protocol.name     |  `http`, `spdy`                 | Conditionally Required                                       |
    * | network.protocol.version  | `1.0`, `1.1`, `2`, `3`          | Recommended                                                  |
    * | server.address            | `example.com`, `10.1.2.80`      | Opt-In                                                       |
    * | server.port               | `80`, `8080`                    | Opt-In                                                       |
    * }}}
    *
    * @param method the http method of the request
    * @param uri the URI of the request
    * @param protocol the protocol of the request
    * @param address the address of the local server
    * @param status the status of the response
    * @param terminationType the termination type
    * @param contentLength the size of the request body
    * @param classifier the classifier to apply
    */
  def recordRequestBodySize(
      method: Method,
      uri: Uri,
      protocol: NetworkProtocol,
      address: Option[SocketAddress[IpAddress]],
      status: Option[Status],
      terminationType: Option[TerminationType],
      contentLength: Option[Long],
      classifier: Option[String],
  ): F[Unit]

  /** Records the size of the response body
    *
    * {{{
    * | Name                      | Example                         | Requirement level                                            |
    * |---------------------------|---------------------------------|--------------------------------------------------------------|
    * | http.request.method       | `GET`, `POST`                   | Required                                                     |
    * | url.scheme                |  `http`, `https`                | Required                                                     |
    * | error.type                | `java.net.UnknownHostException` | Required If request has ended with an error                  |
    * | http.response.status_code | `200`, `404`                    | Conditionally Required If and only if one was received/sent. |
    * | network.protocol.name     |  `http`, `spdy`                 | Conditionally Required                                       |
    * | network.protocol.version  | `1.0`, `1.1`, `2`, `3`          | Recommended                                                  |
    * | server.address            | `example.com`, `10.1.2.80`      | Opt-In                                                       |
    * | server.port               | `80`, `8080`                    | Opt-In                                                       |
    * }}}
    *
    * @param method the http method of the request
    * @param uri the URI of the request
    * @param protocol the protocol of the request
    * @param address the address of the local server
    * @param status the status of the response
    * @param terminationType the termination type
    * @param contentLength the size of the response body
    * @param classifier the classifier to apply
    */
  def recordResponseBodySize(
      method: Method,
      uri: Uri,
      protocol: NetworkProtocol,
      address: Option[SocketAddress[IpAddress]],
      status: Option[Status],
      terminationType: Option[TerminationType],
      contentLength: Option[Long],
      classifier: Option[String],
  ): F[Unit]

  /** Transform the effect of MetricOps using the supplied natural transformation
    *
    * @param fk natural transformation
    * @tparam G the effect to transform to
    * @return a new metric ops in the transformed effect
    */
  def mapK[G[_]](fk: F ~> G): MetricsOps[G] = {
    val ops = this
    new MetricsOps[G] {
      override def increaseActiveRequests(
          method: Method,
          uri: Uri,
          address: Option[SocketAddress[IpAddress]],
          classifier: Option[String],
      ): G[Unit] = fk(ops.increaseActiveRequests(method, uri, address, classifier))
      override def decreaseActiveRequests(
          method: Method,
          uri: Uri,
          address: Option[SocketAddress[IpAddress]],
          classifier: Option[String],
      ): G[Unit] = fk(ops.decreaseActiveRequests(method, uri, address, classifier))
      override def recordHeadersTime(
          method: Method,
          uri: Uri,
          protocol: NetworkProtocol,
          address: Option[SocketAddress[IpAddress]],
          elapsed: FiniteDuration,
          classifier: Option[String],
      ): G[Unit] = fk(ops.recordHeadersTime(method, uri, protocol, address, elapsed, classifier))
      override def recordTotalTime(
          method: Method,
          uri: Uri,
          protocol: NetworkProtocol,
          address: Option[SocketAddress[IpAddress]],
          status: Option[Status],
          terminationType: Option[TerminationType],
          elapsed: FiniteDuration,
          classifier: Option[String],
      ): G[Unit] = fk(
        ops.recordTotalTime(
          method,
          uri,
          protocol,
          address,
          status,
          terminationType,
          elapsed,
          classifier,
        )
      )
      override def recordRequestBodySize(
          method: Method,
          uri: Uri,
          protocol: NetworkProtocol,
          address: Option[SocketAddress[IpAddress]],
          status: Option[Status],
          terminationType: Option[TerminationType],
          contentLength: Option[Long],
          classifier: Option[String],
      ): G[Unit] = fk(
        ops.recordRequestBodySize(
          method,
          uri,
          protocol,
          address,
          status,
          terminationType,
          contentLength,
          classifier,
        )
      )
      override def recordResponseBodySize(
          method: Method,
          uri: Uri,
          protocol: NetworkProtocol,
          address: Option[SocketAddress[IpAddress]],
          status: Option[Status],
          terminationType: Option[TerminationType],
          contentLength: Option[Long],
          classifier: Option[String],
      ): G[Unit] =
        fk(
          ops.recordResponseBodySize(
            method,
            uri,
            protocol,
            address,
            status,
            terminationType,
            contentLength,
            classifier,
          )
        )
    }
  }
}

object MetricsOps {

  /** Given an exclude function, return a 'classifier' function, i.e. for application in
    * org.http4s.server/client.middleware.Metrics#apply.
    *
    * Let's say you want a classifier that excludes integers since your paths consist of:
    *   * GET    /users/{integer} = GET_users_*
    *   * POST   /users           = POST_users
    *   * PUT    /users/{integer} = PUT_users_*
    *   * DELETE /users/{integer} = DELETE_users_*
    *
    * In such a case, we could use:
    *
    * classifierFMethodWithOptionallyExcludedPath(
    *   exclude          = { str: String => scala.util.Try(str.toInt).isSuccess },
    *   excludedValue    = "*",
    *   intercalateValue = "_"
    * )
    *
    * Chris Davenport notes the following on performance considerations of exclude's function value:
    *
    * > It's worth noting that this runs on every segment of a path. So note that if an intermediate Throwables with
    * > Stack traces is known and discarded, there may be a performance penalty, such as the above example with Try(str.toInt).
    * > I benchmarked some approaches and regex matches should generally be preferred over Throwable's
    * > in this position.
    *
    * @param exclude For a given String, namely a path value, determine whether the value gets excluded.
    * @param excludedValue Indicates the String value to be supplied for an excluded path's field.
    * @param pathSeparator Value to use for separating the metrics fields' values
    * @return Request[F] => Option[String]
    */
  def classifierFMethodWithOptionallyExcludedPath[F[_]](
      exclude: String => Boolean,
      excludedValue: String = "*",
      pathSeparator: String = "_",
  ): Request[F] => Option[String] = { (request: Request[F]) =>
    val initial: String = request.method.name

    val excluded =
      request.pathInfo.segments
        .map { segment =>
          val decoded = segment.decoded()
          if (exclude(decoded)) excludedValue else decoded
        }

    val result =
      if (excluded.isEmpty)
        initial
      else
        initial + pathSeparator + Foldable[Vector]
          .intercalate(excluded, pathSeparator)

    Some(result)
  }
}

sealed trait NetworkProtocol {
  def name: String
  def version: HttpVersion
}

object NetworkProtocol {
  def http(version: HttpVersion): NetworkProtocol =
    Impl("http", version)

  private final case class Impl(name: String, version: HttpVersion) extends NetworkProtocol
}

/** Describes the type of abnormal termination */
sealed trait TerminationType

object TerminationType {

  /** Signals just a generic abnormal termination */
  final case class Abnormal(rootCause: Throwable) extends TerminationType

  /** Signals cancelation */
  case object Canceled extends TerminationType

  /** Signals an abnormal termination due to an error processing the request, either at the server or client side */
  final case class Error(rootCause: Throwable) extends TerminationType

  /** Signals a client timing out during a request */
  case object Timeout extends TerminationType
}
