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

@deprecated("Use org.http4s.implicits._ instead", "0.20.0-M2")
trait Http4s extends Http4sInstances with Http4sFunctions with syntax.AllSyntax

@deprecated("Use org.http4s.implicits._ instead", "0.20.0-M2")
object Http4s extends Http4s

@deprecated("Import from or use EntityDecoder/EntityEncoder directly instead", "0.20.0-M2")
trait Http4sInstances

@deprecated("Import from or use EntityDecoder/EntityEncoder directly instead", "0.20.0-M2")
object Http4sInstances extends Http4sInstances

@deprecated("Use org.http4s.qvalue._ or org.http4s.Uri._ instead", "0.20.0-M2")
trait Http4sFunctions

@deprecated("Use org.http4s.qvalue._ or org.http4s.Uri._ instead", "0.20.0-M2")
object Http4sFunctions extends Http4sFunctions
