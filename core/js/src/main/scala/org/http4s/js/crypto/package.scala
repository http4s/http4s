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

package org.http4s.js

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, ArrayBufferView, Uint8Array}
import scala.scalajs.js.|

import scala.annotation.nowarn

package object crypto {

  type BigInteger = Uint8Array

  /** According to [[http://www.w3.org/TR/WebCryptoAPI/#algorithm-dictionary ¶11 Algorithm Identifier]]
    * of the WebCryptoAPI an AlgorithmIdentifier is an `object or DOMString`. We make this more precise
    * here and specify an Algorithm.
    * note: it may be that we can do only with KeyAlgorithmIdentifier and HashAlgorithmIdentifier
    */
  type AlgorithmIdentifier = Algorithm | String

  /** According to [[http://www.w3.org/TR/WebCryptoAPI/#algorithm-dictionary ¶11 Algorithm Identifier]]
    * of the WebCryptoAPI an AlgorithmIdentifier is an `object or DOMString`. We make this more precise
    * here and distinguish the non overlapping classes of Key and Hash Algorithms.
    */
  type KeyAlgorithmIdentifier = KeyAlgorithm | String

  /** According to [[http://www.w3.org/TR/WebCryptoAPI/#algorithm-dictionary ¶11 Algorithm Identifier]]
    * a HashAlgorithmIdentifier is an AlgorithmIdentifier. Here we distinguish between Hash and Key
    * Algorithm Identifiers. At the JS layer these have the same structure.
    */
  type HashAlgorithmIdentifier = HashAlgorithm | String

  @js.native
  @nowarn
  sealed trait BufferSource extends js.Any

  implicit def arrayBuffer2BufferSource(b: ArrayBuffer): BufferSource =
    b.asInstanceOf[BufferSource]

  implicit def arrayBufferView2BufferSource(b: ArrayBufferView): BufferSource =
    b.asInstanceOf[BufferSource]
}
