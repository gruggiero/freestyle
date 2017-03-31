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

import org.scalatest._

import freestyle.implicits._
import freestyle.config._
import freestyle.config.implicits._
import scala.concurrent.{ExecutionContext, Future}
import cats.instances.future._

class ConfigTests extends AsyncWordSpec with Matchers {

  import algebras._

  implicit override def executionContext = ExecutionContext.Implicits.global

  "Shocon config integration" should {

    "allow configuration to be interleaved inside a program monadic flow" in {
      val program = for {
        _      <- app.nonConfig.x
        config <- app.configM.empty
      } yield config
      program.exec[Future] map { _ shouldBe a[Config] }
    }

    "allow configuration to parse strings" in {
      val program = app.configM.parseString("{n = 1}")
      program.exec[Future] map { _.int("n") shouldBe Some(1) }
    }

    "allow configuration to load classpath files" in {
      val program = app.configM.load
      program.exec[Future] map { _.int("s") shouldBe Some(3) }
    }

  }

}

object algebras {
  @free
  trait NonConfig {
    def x: OpSeq[Int]
  }

  implicit def nonConfigHandler: NonConfig.Handler[Future] =
    new NonConfig.Handler[Future] {
      def x: Future[Int] = Future.successful(1)
    }

  @module
  trait App[F[_]] {
    val nonConfig: NonConfig[F]
    val configM: ConfigM[F]
  }

  val app = App[App.Op]

}
