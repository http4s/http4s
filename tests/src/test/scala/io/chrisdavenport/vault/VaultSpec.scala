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

package io.chrisdavenport.vault

import cats.effect._
import org.specs2.mutable.Specification
import org.specs2.ScalaCheck

class VaultSpec extends Specification with ScalaCheck {

  "Vault" should {
    "contain a single value correctly" >> prop { (i: Int) =>
      val emptyVault: Vault = Vault.empty

      Key
        .newKey[SyncIO, Int]
        .map { k =>
          emptyVault.insert(k, i).lookup(k)
        }
        .unsafeRunSync() === Some(i)

    }
    "contain only the last value after inserts" >> prop { (l: List[String]) =>
      val emptyVault: Vault = Vault.empty
      val test: SyncIO[Option[String]] = Key.newKey[SyncIO, String].map { k =>
        l.reverse.foldLeft(emptyVault)((v, a) => v.insert(k, a)).lookup(k)
      }
      test.unsafeRunSync() === l.headOption
    }

    "contain no value after being emptied" >> prop { (l: List[String]) =>
      val emptyVault: Vault = Vault.empty
      val test: SyncIO[Option[String]] = Key.newKey[SyncIO, String].map { k =>
        l.reverse.foldLeft(emptyVault)((v, a) => v.insert(k, a)).empty.lookup(k)
      }
      test.unsafeRunSync() === None
    }

    "not be accessible via a different key" >> prop { (i: Int) =>
      val test = for {
        key1 <- Key.newKey[SyncIO, Int]
        key2 <- Key.newKey[SyncIO, Int]
      } yield Vault.empty.insert(key1, i).lookup(key2)
      test.unsafeRunSync() === None
    }
  }

}
