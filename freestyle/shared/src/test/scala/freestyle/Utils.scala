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

import cats.arrow.FunctionK

object algebras {

  @free
  trait SCtors1 {
    def x(a: Int): OpSeq[Int]
    def y(a: Int): OpSeq[Int]
  }

  @free
  trait SCtors2 {
    def i(a: Int): OpSeq[Int]
    def j(a: Int): OpSeq[Int]
  }

  @free
  trait SCtors3 {
    def o(a: Int): OpSeq[Int]
    def p(a: Int): OpSeq[Int]
  }

  @free
  trait SCtors4 {
    def k(a: Int): OpSeq[Int]
    def m(a: Int): OpSeq[Int]
  }

  @free
  trait MixedFreeS {
    def x: OpPar[Int]
    def y: OpPar[Int]
    def z: OpSeq[Int]
  }

  @free
  trait S1 {
    def x(n: Int): OpSeq[Int]
  }

  @free
  trait S2 {
    def y(n: Int): OpSeq[Int]
  }

}

object modules {

  import algebras._

  @module
  trait M1[F[_]] {
    val sctors1: SCtors1[F]
    val sctors2: SCtors2[F]
  }

  @module
  trait M2[F[_]] {
    val sctors3: SCtors3[F]
    val sctors4: SCtors4[F]
  }

  @module
  trait O1[F[_]] {
    val m1: M1[F]
    val m2: M2[F]
  }

  @module
  trait O2[F[_]] {
    val o1: O1[F]
    val x = 1
    def y = 2
  }

  @module
  trait O3[F[_]] {
    def x = 1
    def y = 2
  }

  @module
  trait StateProp[F[_]] {
    val s1: S1[F]
    val s2: S2[F]
  }

}

object interps {

  import algebras._

  implicit val optionHandler1: FunctionK[SCtors1.Op, Option] = new SCtors1.Handler[Option] {
    def x(a: Int): Option[Int] = Some(a)
    def y(a: Int): Option[Int] = Some(a)
  }

  implicit val listHandler1: FunctionK[SCtors1.Op, List] = new SCtors1.Handler[List] {
    def x(a: Int): List[Int] = List(a)
    def y(a: Int): List[Int] = List(a)
  }

  implicit val optionHandler2: FunctionK[SCtors2.Op, Option] = new SCtors2.Handler[Option] {
    def i(a: Int): Option[Int] = Some(a)
    def j(a: Int): Option[Int] = Some(a)
  }

  implicit val listHandler2: FunctionK[SCtors2.Op, List] = new SCtors2.Handler[List] {
    def i(a: Int): List[Int] = List(a)
    def j(a: Int): List[Int] = List(a)
  }

  implicit val optionHandler3: FunctionK[SCtors3.Op, Option] = new SCtors3.Handler[Option] {
    def o(a: Int): Option[Int] = Some(a)
    def p(a: Int): Option[Int] = Some(a)
  }

  implicit val listHandler3: FunctionK[SCtors3.Op, List] = new SCtors3.Handler[List] {
    def o(a: Int): List[Int] = List(a)
    def p(a: Int): List[Int] = List(a)
  }

  implicit val optionHandler4: FunctionK[SCtors4.Op, Option] = new SCtors4.Handler[Option] {
    def k(a: Int): Option[Int] = Some(a)
    def m(a: Int): Option[Int] = Some(a)
  }

  implicit val listHandler4: FunctionK[SCtors4.Op, List] = new SCtors4.Handler[List] {
    def k(a: Int): List[Int] = List(a)
    def m(a: Int): List[Int] = List(a)
  }

}
