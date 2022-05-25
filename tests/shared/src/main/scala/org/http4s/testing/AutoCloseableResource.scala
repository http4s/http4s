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

package org.http4s.testing

private[http4s] object AutoCloseableResource {

  // TODO: Consider using [[munit.CatsEffectFixtures]] or [[cats.effect.Resource.fromAutoCloseable]] instead
  /** Performs an operation using a resource, and then releases the resource,
    * even if the operation throws an exception. This method behaves similarly
    * to Java's try-with-resources.
    * Ported from the Scala's 2.13 [[scala.util.Using.resource]].
    *
    * @param resource the resource
    * @param body     the operation to perform with the resource
    * @tparam R the type of the resource
    * @tparam A the return type of the operation
    * @return the result of the operation, if neither the operation nor
    *         releasing the resource throws
    */
  private[http4s] def resource[R <: AutoCloseable, A](resource: R)(body: R => A): A = {
    if (resource == null) throw new NullPointerException("null resource")

    var toThrow: Throwable = null

    try body(resource)
    catch {
      case t: Throwable =>
        toThrow = t
        null.asInstanceOf[A]
    } finally
      if (toThrow eq null) resource.close()
      else {
        try resource.close()
        finally throw toThrow
      }
  }
}
