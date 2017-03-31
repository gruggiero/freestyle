/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
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

package freestyle

import cats.{~>, Foldable}
import _root_.doobie.imports.{ConnectionIO, Transactor}
import fs2.util.{Catchable, Suspendable}

object doobie {

  @free sealed trait DoobieM {
    def transact[A](f: ConnectionIO[A]): OpPar[A]
  }

  object implicits {
    implicit def freeStyleDoobieHandler[M[_]: Catchable: Suspendable](
        implicit xa: Transactor[M]): DoobieM.Handler[M] =
      new DoobieM.Handler[M] {
        def transact[A](fa: ConnectionIO[A]): M[A] = xa.trans(fa)
      }

    implicit def freeSLiftDoobie[F[_]: DoobieM]: FreeSLift[F, ConnectionIO] =
      new FreeSLift[F, ConnectionIO] {
        def liftFSPar[A](cio: ConnectionIO[A]): FreeS.Par[F, A] = DoobieM[F].transact(cio)
      }
  }

}
