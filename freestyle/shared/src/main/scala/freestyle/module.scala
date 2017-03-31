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

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.reflect.runtime.universe._

trait FreeModuleLike {
  type Op[A]
}

object coproductcollect {

  def apply[A](a: A): Any = macro materializeImpl[A]

  def materializeImpl[A](c: whitebox.Context)(a: c.Expr[A])(
      implicit foo: c.WeakTypeTag[A]): c.Expr[Any] = {
    import c.universe._

    def fail(msg: String) = c.abort(c.enclosingPosition, msg)

    // s: the companion object of the `@module`-trait
    def findAlgebras(s: ClassSymbol): List[Type] = {

      // cs: the trait annotated as `@module`
      def methodsOf(cs: ClassSymbol): List[MethodSymbol] =
        cs.info.decls.toList.collect { case met: MethodSymbol if met.isAbstract => met }

      // cs: the trait annotated as `@module`
      def isModuleFS(cs: ClassSymbol): Boolean =
        cs.companion.asModule.moduleClass.asClass.baseClasses.exists {
          case x: ClassSymbol => x.name == TypeName("FreeModuleLike")
        }

      // cs: the trait annotated as `@module`
      def fromClass(cs: ClassSymbol): List[Type] =
        methodsOf(cs).flatMap(x => fromMethod(x.returnType))

      def fromMethod(meth: Type): List[Type] =
        meth.typeSymbol match {
          case cs: ClassSymbol =>
            if (isModuleFS(cs)) fromClass(cs) else List(meth.typeConstructor)
          case _ => Nil
        }

      fromClass(s)
    }

    def mkCoproduct(algebras: List[Type]): List[TypeDef] =
      algebras.map(x => TermName(x.toString) ) match {
        case Nil => List( q"type Op[A] = Nothing")

        case List(alg) => List(q"type Op[A] = $alg.Op[A]")

        case alg0 :: alg1 :: algs =>
          val num = algebras.length

          def copName(pos: Int): TypeName = TypeName( if (pos + 1 != num) s"C$pos" else "Op" )

          val copDef1 = q"type ${copName(1)}[A] = cats.data.Coproduct[$alg1.Op, $alg0.Op, A]"

          def copDef(alg: Type, pos: Int ) : TypeDef =
            q"type ${copName(pos)}[A] = cats.data.Coproduct[$alg.Op, ${copName(pos-1)}, A]"

          val copDefs = algebras.zipWithIndex.drop(2).map { case (alg, pos) => copDef(alg, pos) }
          copDef1 :: copDefs
      }

    // The Main Part
    val algebras   = findAlgebras(weakTypeOf[A].typeSymbol.companion.asClass)
    val coproducts = mkCoproduct(algebras)
    //ugly hack because as String it does not typecheck early which we need for types to be in scope
    val parsed     = coproducts.map( cop => c.parse(cop.toString))

    c.Expr[Any]( q"new { ..$parsed }" )
  }

}

@compileTimeOnly("enable macro paradise to expand @module macro annotations")
class module extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro module.impl
}

object module {

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.universe.Tree = {
    import c.universe._
    import scala.reflect.internal._
    import internal.reificationSupport._

    def fail(msg: String) = c.abort(c.enclosingPosition, msg)

    def filterEffectVals( trees: Template): List[ValDef]  =
      trees.collect { case v: ValDef if v.mods.hasFlag(Flag.DEFERRED) => v }

    def mkCompanion( moduleTrait: ClassDef): ModuleDef = {
      val name = moduleTrait.name
      val effectVals: List[ValDef] = filterEffectVals(moduleTrait.impl)

      val tns = moduleTrait.tparams.map(_.name)
      val XX  = freshTermName("XX$")
      val FF  = freshTypeName("FF$")
      val AA  = freshTypeName("AA$")

      val injs: List[ValDef] = effectVals.map {
        case q"$mods val $vname: $algM[..$args]" =>
          q"val $vname: $algM[$FF,..${args.tail}]"
      }

      q"""
        object ${name.toTermName} extends FreeModuleLike {
          val $XX = coproductcollect.apply(this)
          type Op[$AA] = $XX.Op[$AA]

          class To[$FF[_]](implicit ..$injs) extends $name[$FF, ..${tns.tail}]

          implicit def to[$FF[_]](implicit ..$injs): To[$FF] = new To[$FF]()

          def apply[$FF[_]](implicit ev: $name[$FF, ..${tns.tail}]): $name[$FF, ..${tns.tail}] = ev
        }
      """
    }

    // The main part
    annottees match {
      case List(Expr(cls: ClassDef)) =>
        if (cls.mods.hasFlag(Flag.TRAIT | Flag.ABSTRACT)) {
          val userTrait = cls.duplicate
          //  :+ q"val I: Inject[T, F]"
          q"""
            $userTrait
            ${mkCompanion(userTrait)}
          """
        } else
          fail(s"@free requires trait or abstract class")
      case _ =>
        fail(
          s"Invalid @module usage, only traits and abstract classes without companions are supported")
    }
  }
}
