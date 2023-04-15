/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client

import cats.syntax.all._
import com.typesafe.config.ConfigFactory
import org.http4s.Http4sSuite
import org.http4s.headers.`User-Agent`
import org.http4s.internal.parsing.CommonRules.CommentDefaultMaxDepth
import pureconfig._
import pureconfig.error._
import pureconfig.generic.semiauto._
import pureconfig.syntax._

class ConfigTest extends Http4sSuite {

  implicit lazy val readerUserAgent: ConfigReader[`User-Agent`] =
    ConfigReader[String].emap { value =>
      `User-Agent`
        .parse(CommentDefaultMaxDepth)(value)
        .leftMap(f => CannotConvert(s"$value", "User-Agent", f.message))
    }

  implicit lazy val reader: ConfigReader[Config] =
    deriveReader

  test("Creates config") {
    val conf = ConfigFactory.parseString(s"""{
      |    chunk-size = 123
      |}""".stripMargin)
    val Right(res) = conf.to[Config]
    assertEquals(res.chunkSize, 123)
    assertEquals(res.maxTotal, 100)
  }

}
