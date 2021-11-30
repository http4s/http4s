/*
 * Copyright 2016 http4s.org
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

object ClosableResource {

  /** Performs an operation using a resource, and then releases the resource,
    * even if the operation throws an exception. This method behaves similarly
    * to Java's try-with-resources.
    * Ported from the Scala's 2.13 [[scala.util.Using.resource]].
    *
    * @param resource the resource
    * @param body     the operation to perform with the resource
    * @param release  the operation to perform on the finally case
    * @tparam R the type of the resource
    * @tparam A the return type of the operation
    * @return the result of the operation, if neither the operation nor
    *         releasing the resource throws
    */
  def resource[R, A](resource: R)(body: R => A)(release: R => Unit): A = {
    if (resource == null) throw new NullPointerException("null resource")

    var toThrow: Throwable = null

    try body(resource)
    catch {
      case t: Throwable =>
        toThrow = t
        null.asInstanceOf[A]
    } finally if (toThrow eq null) release(resource)
    else {
      try release(resource)
      finally throw toThrow
    }
  }
}
