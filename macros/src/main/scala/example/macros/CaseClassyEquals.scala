package example.macros

import example.macros.enrichment.Enrichments

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("this is a macro annotation")
class CaseClassyEquals extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro CaseClassyEqualsImpl.impl
}

private[macros] class CaseClassyEqualsImpl(val c: whitebox.Context) {
  import c.universe._
  val helpers = Enrichments[c.type](c)
  import helpers._

  def generateEqualsMethod(classDef: ClassDef) = {
    val equalsBody: Tree = {
      val equalsForSameType = {
        val otherTypedDeclaration = ValDef(Modifiers(), TermName("otherTyped"), Ident(classDef.name),
          TypeApply(Select(Ident(TermName("other")), TermName("asInstanceOf")), Ident(classDef.name) :: Nil)
        ) :: Nil
        val paramsEqual = classDef.primaryConstructorParameters.map { param =>
          Apply(Select(Select(This(classDef.name), param.name), TermName("equals")), Select(Ident(TermName("otherTyped")), param.name) :: Nil)
        }
        val allEqual = Apply(Ident(TermName("List")), paramsEqual)
          .untypedMethodApply("forall", Ident(TermName("identity")) :: Nil)

        Block(
          otherTypedDeclaration,
          allEqual
        )
      }
      If(TypeApply(Select(Ident(TermName("other")), TermName("isInstanceOf")), Ident(classDef.name) :: Nil), equalsForSameType, reify(false).tree)
    }
    DefDef(
      Modifiers(Flag.SYNTHETIC | Flag.OVERRIDE),
      TermName("equals"),
      Nil,
      (ValDef(Modifiers(), TermName("other"), Ident(TypeName("Any")), EmptyTree) :: Nil) :: Nil,
      Ident(TypeName("Boolean")),
      equalsBody
    )
  }

  def generateHashCodeMethod(classDef: ClassDef) = {
    val hashCodes = classDef.primaryConstructorParameters.map ( valDef =>
      Ident(valDef.name).untypedMethodApply("hashCode", Nil)
    )
    val reduceFunction = Function(
      ValDef(Modifiers(), TermName("a"), Ident(TypeName("Int")), EmptyTree) ::
      ValDef(Modifiers(), TermName("b"), Ident(TypeName("Int")), EmptyTree) :: Nil,
      reify(31).tree.untypedMethodApply("$times", Ident(TermName("a")) :: Nil).untypedMethodApply("$plus", Ident(TermName("b")) :: Nil)
    )
    val body = Apply(Ident(TermName("List")), hashCodes).untypedMethodApply("reduce", reduceFunction :: Nil)
    DefDef(
      Modifiers(Flag.SYNTHETIC | Flag.OVERRIDE),
      TermName("hashCode"),
      Nil,
      Nil,
      Ident(TypeName("Int")),
      body
    )
  }

  def enrichDefDefs(defDefs: List[DefDef], classDef: ClassDef): List[DefDef] = {
    if(defDefs.exists(_.name.toString == "equals"))
      defDefs
    else {
      val hashCodeDef = generateHashCodeMethod(classDef)
      val equalsDef = generateEqualsMethod(classDef)
      defDefs :+ equalsDef :+ hashCodeDef
    }
  }

  def unprivatizeParamAccessors(classDef: ClassDef): ClassDef = {
    classDef.modifyValDefs {
      case valDef if classDef.primaryConstructorParameters.map(_.name.toString).contains(valDef.name.toString) =>
        val ValDef(mods, name, tpt, rhs) = valDef
        val newMods = ModifiersLenses.flagsLens.modify(flags => flags.without(Flag.LOCAL))(mods)
        ValDef(newMods, name, tpt, rhs)
      case valDef =>
        valDef
    }
  }

  def impl(annottees: c.Tree*): c.Tree = {
    val enriched = annottees.head match {
      case classDef: ClassDef =>
        val finalized = (ClassDefLenses.modifiersLens composeLens ModifiersLenses.flagsLens).modify { flags =>
          flags | Flag.FINAL
        } (classDef)

        val withUnprivatedParamAccessors = unprivatizeParamAccessors(finalized)

        val withNewEquals = ClassDefLenses.defsLens.modify { defDefs =>
          enrichDefDefs(defDefs, classDef)
        }(withUnprivatedParamAccessors)
        withNewEquals +: annottees.tail
      case _ =>
        annottees
    }

    Block(enriched.toList, Literal(Constant(())))
  }
}