package scala.quoted.internal

import scala.quoted._

import dotty.tools.dotc.ast.tpd

/** Quoted type (or kind) `T` backed by a tree */
final class Type(val typeTree: tpd.Tree, val scopeId: Int) extends scala.quoted.Type[?] {
  override def equals(that: Any): Boolean = that match {
    case that: Type => typeTree ==
      // TastyTreeExpr are wrappers around trees, therfore they are equals if their trees are equal.
      // All scopeId should be equal unless two different runs of the compiler created the trees.
      that.typeTree && scopeId == that.scopeId
    case _ => false
  }

  /** View this expression `quoted.Type[T]` as a `TypeTree` */
  def unseal(using qctx: QuoteContext): qctx.reflect.TypeTree =
    checkScopeId(qctx.hashCode)
    typeTree.asInstanceOf[qctx.reflect.TypeTree]

  def checkScopeId(expectedScopeId: Int): Unit =
    if expectedScopeId != scopeId then
      throw new ScopeException("Cannot call `scala.quoted.staging.run(...)` within a macro or another `run(...)`")

  override def hashCode: Int = typeTree.hashCode
  override def toString: String = "'[ ... ]"
}
