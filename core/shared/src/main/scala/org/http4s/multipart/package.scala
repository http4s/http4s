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

package org.http4s

/** This package is the start of a multipart implementation for http4s.
  * It is still deficient in a few ways:
  *
  * - All encoding is chunked transfers, except for entities small
  * enough to fit into the blaze buffer.  This irritates some server
  * implementations.
  *
  * - When decoding, chunks are kept in memory.  Large ones should be
  * buffered to a temp file.
  *
  * - It's a bit handwavy around character sets.  Things probably go
  * horribly wrong if you're not UTF-8.
  *
  * - This module is lightly tested, and its API should be considered
  * experimental.
  *
  * Enter this package at your own risk, but we'd love the feedback.
  */
package object multipart
