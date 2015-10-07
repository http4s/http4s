package org.http4s
package server

package object syntax {

  final implicit class ServiceOps[A, B: Fallthrough](val service: Service[A, B]) {
    def ||    (fallback: Service[A, B]) = orElse(fallback)
    def orElse(fallback: Service[A, B]) = Service.withFallback(fallback)(service)
  }

}
