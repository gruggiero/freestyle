---
layout: docs
title: Cats
permalink: /docs/cats/
---

# Cats

Freestyle is build on top of Cats' [`Free`](http://typelevel.org/cats/datatypes/freemonad.html) and [`FreeApplicative`](http://typelevel.org/cats/datatypes/freeapplicative.html) and `FreeS` and `FreeS.Par` are just type aliases.

```tut:silent
import cats.free.{ Free, FreeApplicative }

object aliases {
  type FreeS[F[_], A] = Free[FreeApplicative[F, ?], A]

  object FreeS {
    type Par[F[_], A] = FreeApplicative[F, A]
  }
}
```

A [freestyle module](/docs/modules/) (with `@module`) is an easy way to combine multiple algebras and creates a `Coproduct` of the underlying algebras together with the necessary `Inject` instances.

- A `Coproduct` is a combination of data types. An operation of type `type FooBarOp[A] = Coproduct[FooOp, BarOp, A]` can either by a `FooOp` or a `BarOp`.
- `Inject` is a type class which can inject a data type in a `Coproduct` containing that specific data type. An `Inject[FooOp, FooBarOp]` instance can inject a `FooOp` operation into the `FooBarOp` coproduct.

## `FreeS` with Cats

As `FreeS` is a [monad](http://typelevel.org/cats/typeclasses/monad.html) and `FreeS.Par` an [applicative](http://typelevel.org/cats/typeclasses/applicative.html), they can be used like any other monad/applicative in Cats.

To see how we can use `FreeS` and `FreeS.Par` in combination with existing Cats functions, we will create a simple algebra with a sum and a product operation.

```tut:book
import freestyle._

@free trait Calc[F[_]] {
  def sum(a: Int, b: Int): FreeS.Par[F, Int]
  def product(a: Int, b: Int): FreeS.Par[F, Int]
}
```

A simple `Id` handler.

```tut:book
import cats.Id

implicit val idHandler = new Calc.Handler[Id] {
  def sum(a: Int, b: Int) = a + b
  def product(a: Int, b: Int) = a * b
}
```

A handler translating the `Calc` algebra to `Future`, which introduces a little bit of artificial latency.

```tut:book
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

implicit val futureHandler = new Calc.Handler[Future] {
  def sum(a: Int, b: Int) =
    Future { Thread.sleep(a * 100L); a + b }
  def product(a: Int, b: Int) =
    Future { Thread.sleep(a * 100L); a * b }
}
```

We can use `Calc#sum` to create a function which increments an integer.

We have `incrPar` and `incrSeq`, where multiple `incrPar` calls could potentially be executed in parallel.

```tut:book
val incrPar: Int => FreeS.Par[Calc.Op, Int] =
  Calc[Calc.Op].sum(_, 1)

val incrSeq: Int => FreeS[Calc.Op, Int] =
  incrPar.andThen(_.freeS)
```

### Traversing

We will use `traverse` (provided by Cats' [`Traverse`](http://typelevel.org/cats/typeclasses/traverse.html)) to increment a list of integers with `incrPar` and `incrSeq`.

```tut:book
import cats.implicits._
import freestyle.implicits._

val numbers = List.range(1, 10)

val traversingPar = numbers.traverse(incrPar)
val traversingSeq = numbers.traverse(incrSeq)
```

Executing these with our `Id` handler:

```tut:book
traversingPar.exec[Id]
traversingSeq.exec[Id]
```

A simple timer method giving a rough estimate of the execution time of a piece of code.

```tut:book
def simpleTime[A](th: => A): A = {
  val start = System.currentTimeMillis
  val result = th
  val end = System.currentTimeMillis
  println("time: " + (end - start))
  result
}
```

When we execute the increment traversals again using `Future`, we can observe the side effect that the parallel execution is quicker than the sequential.

```tut:book
import freestyle.nondeterminism._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

val futPar = traversingPar.exec[Future]
simpleTime(Await.result(futPar, Duration.Inf))

val futSeq = traversingSeq.exec[Future]
simpleTime(Await.result(futSeq, Duration.Inf))
```

### Applicative and Monadic behaviour

We can combine applicative and monadic steps by using the cartesian builder (`|@|`) inside a for comprehension.

```tut:book
val incrAndMultiply =
  for {
    ab <- (incrPar(5) |@| incrPar(6)).tupled
    (a, b) = ab
    c <- Calc[Calc.Op].product(a, b)
    d <- FreeS.pure(c + 1)
  } yield d

incrAndMultiply.exec[Id]
```

In the first line of the for comprehension, the two applicative operations using `incrPar` are independent and could be executed in parallel. In the last line we are using `FreeS.pure` as a handy alternative for `(c + 1).pure[FreeS[Calc.Op, ?]]` or `Applicative[FreeS[CalcOp, ?]].pure(c + 1)`.

To see the side effect of replacing multiple independent monadic steps with one applicative step, we can again execute two similar programs using `Future`.

```tut:book
val incrSeqSum = for {
  a <- incrSeq(1)
  b <- incrSeq(2)
  c <- Calc[Calc.Op].sum(a, b)
} yield c

val incrParSum = for {
  ab <- (incrPar(1) |@| incrPar(2)).tupled
  c  <- Function.tupled(Calc[Calc.Op].sum _)(ab)
} yield c

val futSeq2 = incrSeqSum.exec[Future]
simpleTime(Await.result(futSeq2, Duration.Inf))

val futPar2 = incrParSum.exec[Future]
simpleTime(Await.result(futPar2, Duration.Inf))
```

## Cats data types

Imagine the case where we would like to execute our simple calculations by a (remote) service which needs some configuration. Our `Calc` algebra has no parameters taking configuration and we don't want to add this service specific configuration to the `Calc` algebra because that would mean that we also need to pass the configuration when we want to perform some calculations ourselves with our `Id` or `Future` handlers. A possible solution is to use the [`Kleisli`](http://typelevel.org/cats/datatypes/kleisli.html) or `ReaderT` data type from Cats as target for our `Calc` operations.

`Kleisli` is essentially just a function where you can sort of work with the eventual result in a for comprehension, `map` function, ... while you only supply the input to the `Kleisli` function when you actually need the final result.

Our demo configuration will just contain a number that we will add to every computation, but in a more realistic case it could contain an API key, a URI, etc.

```tut:book
case class Config(n: Int)
```

We will use `Reader[Config, ?]`, which is a type alias for `ReaderT[Id, Config, ?]`, as target here.

```tut:book
import cats.data.Reader
type WithConfig[A] = Reader[Config, A]

implicit val readerHandler =
  new Calc.Handler[WithConfig] {
    def sum(a: Int, b: Int) =
      Reader { cfg => cfg.n + a + b }
    def product(a: Int, b: Int) =
      Reader { cfg => cfg.n + a * b }
  }
```

With this `Reader` handler in place, we can translate some of the previous programs and supply the configuration to get the end result with `Kleisli#run`.

```tut:book
val configTraversing = traversingSeq.exec[WithConfig]
val configIncrSum    = incrParSum.exec[WithConfig]

val cfg = Config(1000)
configTraversing.run(cfg)
configIncrSum.run(cfg)
```

The Cats data types can be used as target of a freestyle program, but they cooperate also perfectly well with `FreeS` and `FreeS.Par`. In the [stack example](/docs/stack/) documentation section, the `getCustomer` method uses [`OptionT`](http://typelevel.org/cats/datatypes/optiont.html) to combine `FreeS` programs.

## Standing on the shoulders of giant Cats

Freestyle is only possible because of the foundational abstractions provided by Cats. Freestyle shares Cats philosophy to make functional programming in Scala simpler and more approachable to all Scala developers. 

Freestyle tries to reduce the boilerplate needed to create programs using free algebras and provides ready to use effect algebras and integrations with other libraries, making it easier to work and get started with `Free` and consorts.
