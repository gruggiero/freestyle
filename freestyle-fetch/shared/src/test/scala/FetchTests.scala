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
import _root_.fetch._
import _root_.fetch.implicits._

import freestyle.fetch._
import freestyle.implicits._
import freestyle.fetch.implicits._
import scala.concurrent.{ExecutionContext, Future}

class FetchTests extends AsyncWordSpec with Matchers {

  import algebras._
  import fetches._

  implicit override def executionContext = ExecutionContext.Implicits.global

  "Fetch Freestyle integration" should {

    "allow a fetch to be interleaved inside a program monadic flow" in {
      val program = for {
        a <- app.nonFetch.x
        b <- app.fetchM.runA(fetchString(a)).freeS
        c <- FreeS.pure(1)
      } yield a + b + c
      program.exec[Future] map { _ shouldBe "111" }
    }

    "allow fetch syntax to lift to FreeS" in {
      val program: FreeS[App.Op, String] = for {
        a <- app.nonFetch.x
        b <- fetchString(a).liftFS[App.Op]
        c <- app.nonFetch.x
      } yield a + b + c
      program.exec[Future] map { _ shouldBe "111" }
    }

    "allow fetch syntax to lift to FreeS.Par" in {
      val program: FreeS[App.Op, String] = for {
        a <- app.nonFetch.x
        b <- fetchString(a).liftFSPar[App.Op].freeS
        c <- app.nonFetch.x
      } yield a + b + c
      program.exec[Future] map { _ shouldBe "111" }
    }

  }

}

object algebras {
  @free
  trait NonFetch {
    def x: OpSeq[Int]
  }

  implicit def nonFetchHandler: NonFetch.Handler[Future] =
    new NonFetch.Handler[Future] {
      def x: Future[Int] = Future.successful(1)
    }

  @module
  trait App[F[_]] {
    val nonFetch: NonFetch[F]
    val fetchM: FetchM[F]
  }

  val app = App[App.Op]

}

object datasources {
  import cats.data.NonEmptyList
  import cats.instances.list._

  implicit object ToStringSource extends DataSource[Int, String] {
    override def name = "ToString"
    override def fetchOne(id: Int): Query[Option[String]] =
      Query.sync(Some(id.toString))
    override def fetchMany(ids: NonEmptyList[Int]): Query[Map[Int, String]] =
      Query.sync(ids.toList.map(i => (i, i.toString)).toMap)
  }

}

object fetches {
  import datasources._
  def fetchString(n: Int): Fetch[String] =
    Fetch(n) // or, more explicitly: Fetch(n)(ToStringSource)
}
