package org.http4s
package client

import cats.data._
import org.http4s.client.Timeout._
import scala.concurrent.duration._

final class TimeoutsSpec extends Http4sSpec {
  "Timeouts" should {
    "fail validation if responseHeaderTimeout > requestTimeout" in {
      val responseHeaderTimeout: ResponseHeaderTimeout = ResponseHeaderTimeout(2.minutes)
      val requestTimeout: RequestTimeout = RequestTimeout(1.minutes)
      Timeouts.validatedNec(
        ConnectTimeout.Inf,
        IdleTimeout.Inf,
        requestTimeout,
        responseHeaderTimeout
      ) must_== Validated.invalidNec(
        Timeouts.Error.TimeoutOrderingError(responseHeaderTimeout, requestTimeout))
    }
    "not fail validation if responseHeaderTimeout < requestTimeout" in {
      val responseHeaderTimeout: ResponseHeaderTimeout = ResponseHeaderTimeout(1.minutes)
      val requestTimeout: RequestTimeout = RequestTimeout(2.minutes)
      Timeouts
        .validatedNec(
          ConnectTimeout.Inf,
          IdleTimeout.Inf,
          requestTimeout,
          responseHeaderTimeout
        )
        .isValid
    }
    "fail validation if responseHeaderTimeout == requestTimeout" in {
      val responseHeaderTimeout: ResponseHeaderTimeout = ResponseHeaderTimeout(1.minutes)
      val requestTimeout: RequestTimeout = RequestTimeout(1.minutes)
      Timeouts.validatedNec(
        ConnectTimeout.Inf,
        IdleTimeout.Inf,
        requestTimeout,
        responseHeaderTimeout
      ) must_== Validated.invalidNec(
        Timeouts.Error.TimeoutOrderingError(responseHeaderTimeout, requestTimeout))
    }
    "fail validation if responseHeaderTimeout > idleTimeout" in {
      val responseHeaderTimeout: ResponseHeaderTimeout = ResponseHeaderTimeout(2.minutes)
      val idleTimeout: IdleTimeout = IdleTimeout(1.minutes)
      Timeouts.validatedNec(
        ConnectTimeout.Inf,
        idleTimeout,
        RequestTimeout.Inf,
        responseHeaderTimeout
      ) must_== Validated.invalidNec(
        Timeouts.Error.TimeoutOrderingError(responseHeaderTimeout, idleTimeout))
    }
    "not fail validation if responseHeaderTimeout < idleTimeout" in {
      val responseHeaderTimeout: ResponseHeaderTimeout = ResponseHeaderTimeout(1.minutes)
      val idleTimeout: IdleTimeout = IdleTimeout(2.minutes)
      Timeouts
        .validatedNec(
          ConnectTimeout.Inf,
          idleTimeout,
          RequestTimeout.Inf,
          responseHeaderTimeout
        )
        .isValid
    }
    "fail validation if responseHeaderTimeout == idleTimeout" in {
      val responseHeaderTimeout: ResponseHeaderTimeout = ResponseHeaderTimeout(1.minutes)
      val idleTimeout: IdleTimeout = IdleTimeout(1.minutes)
      Timeouts.validatedNec(
        ConnectTimeout.Inf,
        idleTimeout,
        RequestTimeout.Inf,
        responseHeaderTimeout
      ) must_== Validated.invalidNec(
        Timeouts.Error.TimeoutOrderingError(responseHeaderTimeout, idleTimeout))
    }
    "fail validation if requestTimeout > idleTimeout" in {
      val idleTimeout: IdleTimeout = IdleTimeout(1.minutes)
      val requestTimeout: RequestTimeout = RequestTimeout(2.minutes)
      Timeouts.validatedNec(
        ConnectTimeout.Inf,
        idleTimeout,
        requestTimeout,
        ResponseHeaderTimeout.Inf
      ) must_== Validated.invalidNec(
        Timeouts.Error.TimeoutOrderingError(requestTimeout, idleTimeout))
    }
    "not fail validation if requestTimeout < idleTimeout" in {
      val idleTimeout: IdleTimeout = IdleTimeout(2.minutes)
      val requestTimeout: RequestTimeout = RequestTimeout(1.minutes)
      Timeouts
        .validatedNec(
          ConnectTimeout.Inf,
          idleTimeout,
          requestTimeout,
          ResponseHeaderTimeout.Inf
        )
        .isValid
    }
    "fail validation if responseHeaderTimeout == idleTimeout" in {
      val idleTimeout: IdleTimeout = IdleTimeout(1.minutes)
      val requestTimeout: RequestTimeout = RequestTimeout(1.minutes)
      Timeouts.validatedNec(
        ConnectTimeout.Inf,
        idleTimeout,
        requestTimeout,
        ResponseHeaderTimeout.Inf
      ) must_== Validated.invalidNec(
        Timeouts.Error.TimeoutOrderingError(requestTimeout, idleTimeout))
    }
  }
}
