/*
 * Adapted from the Play Framework
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 * https://raw.githubusercontent.com/playframework/playframework/a3d8d0ab24dbeb35570a837b01b3b3d3c00cf621/framework/src/iteratees/src/main/scala/play/api/libs/iteratee/Execution.scala
 */
package org.http4s.util

import java.util.{List => JList, Collections, ArrayDeque, Deque}
import java.util.concurrent.{AbstractExecutorService, TimeUnit}

import scala.concurrent.ExecutionContextExecutorService

object TrampolineExecutionContext
    extends AbstractExecutorService
    with ExecutionContextExecutorService {
  private[this] val local = new ThreadLocal[Deque[Runnable]]

  def execute(runnable: Runnable): Unit =
    local.get match {
      case null =>
        // Since there is no local queue, we need to install one and
        // start our trampolining loop.
        try {
          val installedQueue = new ArrayDeque[Runnable](4)
          installedQueue.addLast(runnable)
          local.set(installedQueue)
          while (!installedQueue.isEmpty) {
            val runnable = installedQueue.removeFirst()
            runnable.run()
          }
        } finally {
          // We've emptied the queue, so tidy up.
          local.set(null)
        }
      case existingQueue =>
        // There's already a local queue that is being executed.
        // Just stick our runnable on the end of that queue. The
        // runnable will eventually be run by the call to
        // `execute` that installed the queue.
        existingQueue.addLast(runnable)
    }

  def reportFailure(t: Throwable): Unit = t.printStackTrace()

  override def shutdown(): Unit = {}

  override def isTerminated: Boolean = false

  override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false

  override def shutdownNow(): JList[Runnable] = Collections.emptyList[Runnable]

  override def isShutdown: Boolean = false
}
