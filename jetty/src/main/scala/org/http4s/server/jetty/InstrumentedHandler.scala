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
case class InstrumentedHandler(registry:MetricRegistry , prefix:Option[String] = None)  extends HandlerWrapper {

  var name:String                = null
  // the requests handled by this handler, excluding active
  var requests:Timer             = null
  // the number of dispatches seen by this handler, excluding active
  var dispatches:Timer           = null
  // the number of active requests
  var activeRequests:Counter     = null
  // the number of active dispatches
  var activeDispatches:Counter   = null
  // the number of requests currently suspended.
  var activeSuspended:Counter    = null
  // the number of requests that have been asynchronously dispatched
  var asyncDispatches:Meter      = null
  // the number of requests that expired while suspended
  var asyncTimeouts:Meter        = null
  var responses:Array[Meter]     = null
  var getRequests:Timer          = null
  var postRequests:Timer         = null
  var headRequests:Timer         = null
  var putRequests:Timer          = null
  var deleteRequests:Timer       = null
  var optionsRequests:Timer      = null
  var traceRequests:Timer        = null
  var connectRequests:Timer      = null
  var moveRequests:Timer         = null
  var otherRequests:Timer        = null
  var listener:AsyncListener     = null


    override protected def doStart  {
        super.doStart()

        val _prefix = prefix.fold(registryName(getHandler().getClass(), name) )(p => registryName(p, name))

        requests         = registry.timer(registryName(_prefix,   "requests"))
        dispatches       = registry.timer(registryName(_prefix,   "dispatches"))
        activeRequests   = registry.counter(registryName(_prefix, "active-requests"))
        activeDispatches = registry.counter(registryName(_prefix, "active-dispatches"))
        activeSuspended  = registry.counter(registryName(_prefix, "active-suspended"))
        asyncDispatches  = registry.meter(registryName(_prefix,   "async-dispatches"))
        asyncTimeouts    = registry.meter(registryName(_prefix,   "async-timeouts"))

        responses = Array[Meter](
                registry.meter(registryName(_prefix, "1xx-responses")), // 1xx
                registry.meter(registryName(_prefix, "2xx-responses")), // 2xx
                registry.meter(registryName(_prefix, "3xx-responses")), // 3xx
                registry.meter(registryName(_prefix, "4xx-responses")), // 4xx
                registry.meter(registryName(_prefix, "5xx-responses"))) // 5xx

                
        getRequests       = registry.timer(registryName(_prefix, "get-requests"))
        postRequests      = registry.timer(registryName(_prefix, "post-requests"))
        headRequests      = registry.timer(registryName(_prefix, "head-requests"))
        putRequests       = registry.timer(registryName(_prefix, "put-requests"))
        deleteRequests    = registry.timer(registryName(_prefix, "delete-requests"))
        optionsRequests   = registry.timer(registryName(_prefix, "options-requests"))
        traceRequests     = registry.timer(registryName(_prefix, "trace-requests"))
        connectRequests   = registry.timer(registryName(_prefix, "connect-requests"))
        moveRequests      = registry.timer(registryName(_prefix, "move-requests"))
        otherRequests     = registry.timer(registryName(_prefix, "other-requests"))

        registry.register(registryName(_prefix, "percent-4xx-1m"), new RatioGauge() {
            protected def getRatio() = {
              Ratio.of(responses(3).getOneMinuteRate(),
                       requests.getOneMinuteRate())
            }
        })
        registry.register(registryName(_prefix, "percent-4xx-5m"), new RatioGauge() {
            protected def getRatio() = 
              Ratio.of(responses(3).getFiveMinuteRate(),requests.getFiveMinuteRate())
        })
        registry.register(registryName(_prefix, "percent-4xx-15m"), new RatioGauge() {
            protected def getRatio() = 
              Ratio.of(responses(3).getFifteenMinuteRate(),requests.getFifteenMinuteRate())
            
        })
        registry.register(registryName(_prefix, "percent-5xx-1m"), new RatioGauge() {
            protected def getRatio() = 
              Ratio.of(responses(4).getOneMinuteRate(),requests.getOneMinuteRate())
            
        })
        registry.register(registryName(_prefix, "percent-5xx-5m"), new RatioGauge() {
            protected def getRatio() = 
              Ratio.of(responses(4).getFiveMinuteRate(),requests.getFiveMinuteRate())
        })
        registry.register(registryName(_prefix, "percent-5xx-15m"), new RatioGauge() {
            protected def getRatio() = 
              Ratio.of(responses(4).getFifteenMinuteRate(),requests.getFifteenMinuteRate())
        })

        this.listener = new AsyncListener() {
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

    def requestTimer( method:String) = HttpMethod.fromString(method) match  {
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

    def updateResponses(request:Request) = {
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