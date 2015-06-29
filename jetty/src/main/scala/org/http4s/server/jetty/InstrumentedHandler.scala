/*
 * Licensed under the Apache License, Version 2.0
 * Copyright 2010-2012 Coda Hale and Yammer, Inc.
 * https://github.com/dropwizard/metrics/blob/v3.1.0/LICENSE
 */
package org.http4s.server.jetty

import com.codahale.metrics.Counter
import com.codahale.metrics.Meter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.RatioGauge
import com.codahale.metrics.RatioGauge._
import com.codahale.metrics.Timer
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.server.AsyncContextState
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.HttpChannelState
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.HandlerWrapper

import javax.servlet.AsyncEvent
import javax.servlet.AsyncListener
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import HttpMethod._

import com.codahale.metrics.MetricRegistry.{name => registryName}
/**
 * A Jetty {@link Handler} which records various metrics about an underlying {@link Handler}
 * instance.
 *
 * <a href="https://github.com/http4s/http4s/issues/204">See http4s/http4s#204</a>.
 */
protected case class InstrumentedHandler(registry:MetricRegistry , _prefix:Option[String] = None) extends HandlerWrapper {

  var name:String                               = null

  private val registerTimer:String   => Timer   = name => registry.timer(registryName(prefix,   name))
  private val registerMeter:String   => Meter   = name => registry.meter(registryName(prefix,   name))
  private val registerCounter:String => Counter = name => registry.counter(registryName(prefix,   name))
  private val listener:AsyncListener            = new AsyncListener() {
            def onTimeout(event:AsyncEvent )   = asyncTimeouts.mark()
            def onStartAsync(event:AsyncEvent) = event.getAsyncContext().addListener(this)
            def onError(event:AsyncEvent )     = ()
            def onComplete(event:AsyncEvent)   = {
                val state:AsyncContextState =  event.getAsyncContext().asInstanceOf[AsyncContextState]
                val request:Request =  state.getRequest().asInstanceOf[Request]
                updateResponses(request)
                if (state.getHttpChannelState().getState() != HttpChannelState.State.DISPATCHED) {
                    activeSuspended.dec()
                }
            }
        }

  private lazy val prefix            = _prefix.fold(registryName(getHandler().getClass(), name) )(p => registryName(p, name))
  // the requests handled by this handler, excluding active
  private lazy val requests          = registerTimer("requests")
  // the number of dispatches seen by this handler, excluding active
  private lazy val  dispatches       = registerTimer("dispatches")
  // the number of active requests
  private lazy val  activeRequests   = registerCounter("active-requests")
  // the number of active dispatches
  private lazy val  activeDispatches = registerCounter("active-dispatches")
  // the number of requests currently suspended.
  private lazy val  activeSuspended  = registerCounter("active-suspended")
  // the number of requests that have been asynchronously dispatched
  private lazy val  asyncDispatches  = registerMeter("async-dispatches")
  // the number of requests that expired while suspended
  private lazy val  asyncTimeouts    = registerMeter(   "async-timeouts")
  private lazy val  responses        = Array[Meter](
                                        registerMeter( "1xx-responses"), // 1xx
                                        registerMeter( "2xx-responses"), // 2xx
                                        registerMeter( "3xx-responses"), // 3xx
                                        registerMeter( "4xx-responses"), // 4xx
                                        registerMeter( "5xx-responses")) // 5xx
  private lazy val  getRequests      = registerTimer("get-requests")
  private lazy val  postRequests     = registerTimer( "post-requests")
  private lazy val  headRequests     = registerTimer( "head-requests")
  private lazy val  putRequests      = registerTimer( "put-requests")
  private lazy val  deleteRequests   = registerTimer( "delete-requests")
  private lazy val  optionsRequests  = registerTimer( "options-requests")
  private lazy val  traceRequests    = registerTimer( "trace-requests")
  private lazy val  connectRequests  = registerTimer( "connect-requests")
  private lazy val  moveRequests     = registerTimer( "move-requests")
  private lazy val  otherRequests    = registerTimer( "other-requests")

  override protected def doStart  {
      super.doStart()
      registry.register(registryName(prefix, "percent-4xx-1m"), new RatioGauge() {
          protected def getRatio() = {
            Ratio.of(responses(3).getOneMinuteRate(),
                     requests.getOneMinuteRate())
          }
      })
      registry.register(registryName(prefix, "percent-4xx-5m"), new RatioGauge() {
          protected def getRatio() =
            Ratio.of(responses(3).getFiveMinuteRate(),requests.getFiveMinuteRate())
      })
      registry.register(registryName(prefix, "percent-4xx-15m"), new RatioGauge() {
          protected def getRatio() =
            Ratio.of(responses(3).getFifteenMinuteRate(),requests.getFifteenMinuteRate())

      })
      registry.register(registryName(prefix, "percent-5xx-1m"), new RatioGauge() {
          protected def getRatio() =
            Ratio.of(responses(4).getOneMinuteRate(),requests.getOneMinuteRate())

      })
      registry.register(registryName(prefix, "percent-5xx-5m"), new RatioGauge() {
          protected def getRatio() =
            Ratio.of(responses(4).getFiveMinuteRate(),requests.getFiveMinuteRate())
      })
      registry.register(registryName(prefix, "percent-5xx-15m"), new RatioGauge() {
          protected def getRatio() =
            Ratio.of(responses(4).getFifteenMinuteRate(),requests.getFifteenMinuteRate())
      })
  }

    override def handle(path:String,
                        request:Request,
                        httpRequest:HttpServletRequest,
                        httpResponse:HttpServletResponse):Unit = {
        activeDispatches.inc()
        val state = request.getHttpChannelState()
        val start = if (state.isInitial()) {
            // new request
            activeRequests.inc()
            request.getTimeStamp()
        } else {
            // resumed request
            activeSuspended.dec()
            if (state.getState() == HttpChannelState.State.DISPATCHED) {
                asyncDispatches.mark()
            }
            System.currentTimeMillis()
        }

        try {
            super.handle(path, request, httpRequest, httpResponse)
        } finally {
            val now = System.currentTimeMillis()
            val dispatched = now - start

            activeDispatches.dec()
            dispatches.update(dispatched, TimeUnit.MILLISECONDS)

            if (state.isSuspended()) {
                if (state.isInitial()) {
                    state.addListener(listener)
                }
                activeSuspended.inc()
            } else if (state.isInitial()) {
                updateResponses(request)
            }
            // else onCompletion will handle it.
        }
    }

    private def requestTimer( method:String) = HttpMethod.fromString(method) match  {
      case GET     => getRequests
      case POST    => postRequests
      case PUT     => putRequests
      case HEAD    => headRequests
      case DELETE  => deleteRequests
      case OPTIONS => optionsRequests
      case TRACE   => traceRequests
      case CONNECT => connectRequests
      case MOVE    => moveRequests
      case default => otherRequests
    }

  private def updateResponses(request:Request) = {
      val response = request.getResponse().getStatus() / 100
      if (response >= 1 && response <= 5) {
          responses(response - 1).mark()
      }
      activeRequests.dec()
        val elapsedTime = System.currentTimeMillis() - request.getTimeStamp()
        requests.update(elapsedTime, TimeUnit.MILLISECONDS)
        requestTimer(request.getMethod()).update(elapsedTime, TimeUnit.MILLISECONDS)
    }

}