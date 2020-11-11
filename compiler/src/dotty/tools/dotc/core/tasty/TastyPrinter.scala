package dotty.tools.dotc
package core
package tasty

import dotty.tools.tasty.{TastyBuffer, TastyReader}
import TastyBuffer.{Addr, NameRef}

import Contexts._, Decorators._
import Names.Name
import TastyUnpickler._
import util.Spans.offsetToInt
import printing.Highlighting._

object TastyPrinter:
  def show(bytes: Array[Byte])(using Context): String =
    val printer =
      if ctx.settings.color.value == "never" then new TastyPrinter(bytes)
      else new TastyAnsiiPrinter(bytes)
    printer.showContents()

class TastyPrinter(bytes: Array[Byte]) {

  private val sb: StringBuilder = new StringBuilder

  private val unpickler: TastyUnpickler = new TastyUnpickler(bytes)
  import unpickler.{nameAtRef, unpickle}

  private def nameToString(name: Name): String = name.debugString

  private def nameRefToString(ref: NameRef): String = nameToString(nameAtRef(ref))

  private def printNames(): Unit =
    for ((name, idx) <- nameAtRef.contents.zipWithIndex) {
      val index = nameStr("%4d".format(idx))
      sb.append(index).append(": ").append(nameToString(name)).append("\n")
    }

  def showContents(): String = {
    sb.append("Names:\n")
    printNames()
    sb.append("\n")
    sb.append("Trees:\n")
    unpickle(new TreeSectionUnpickler) match {
      case Some(s) => sb.append(s)
      case _ =>
    }
    sb.append("\n\n")
    unpickle(new PositionSectionUnpickler) match {
      case Some(s) => sb.append(s)
      case _ =>
    }
    sb.append("\n\n")
    unpickle(new CommentSectionUnpickler) match {
      case Some(s) => sb.append(s)
      case _ =>
    }
    sb.result
  }

  class TreeSectionUnpickler extends SectionUnpickler[String](TreePickler.sectionName) {
    import dotty.tools.tasty.TastyFormat._

    private val sb: StringBuilder = new StringBuilder

    def unpickle(reader: TastyReader, tastyName: NameTable): String = {
      import reader._
      var indent = 0
      def newLine() = {
        val length = treeStr("%5d".format(index(currentAddr) - index(startAddr)))
        sb.append(s"\n $length:" + " " * indent)
      }
      def printNat() = sb.append(treeStr(" " + readNat()))
      def printName() = {
        val idx = readNat()
        sb.append(nameStr(" " + idx + " [" + nameRefToString(NameRef(idx)) + "]"))
      }
      def printTree(): Unit = {
        newLine()
        val tag = readByte()
        sb.append(" ").append(astTagToString(tag))
        indent += 2
        if (tag >= firstLengthTreeTag) {
          val len = readNat()
          sb.append(s"(${lengthStr(len.toString)})")
          val end = currentAddr + len
          def printTrees() = until(end)(printTree())
          tag match {
            case RENAMED =>
              printName(); printName()
            case VALDEF | DEFDEF | TYPEDEF | TYPEPARAM | PARAM | NAMEDARG | BIND =>
              printName(); printTrees()
            case REFINEDtype | TERMREFin | TYPEREFin | SELECTin =>
              printName(); printTree(); printTrees()
            case RETURN | HOLE =>
              printNat(); printTrees()
            case METHODtype | POLYtype | TYPELAMBDAtype =>
              printTree()
              while (currentAddr.index < end.index && !isModifierTag(nextByte)) { printTree(); printName(); }
              printTrees()
            case PARAMtype =>
              printNat(); printNat()
            case _ =>
              printTrees()
          }
          if (currentAddr != end) {
            sb.append(s"incomplete read, current = $currentAddr, end = $end\n")
            goto(end)
          }
        }
        else if (tag >= firstNatASTTreeTag) {
          tag match {
            case IDENT | IDENTtpt | SELECT | SELECTtpt | TERMREF | TYPEREF | SELFDEF => printName()
            case _ => printNat()
          }
          printTree()
        }
        else if (tag >= firstASTTreeTag)
          printTree()
        else if (tag >= firstNatTreeTag)
          tag match {
            case TERMREFpkg | TYPEREFpkg | STRINGconst | IMPORTED => printName()
            case _ => printNat()
          }
        indent -= 2
      }
      sb.append(s"start = ${reader.startAddr}, base = $base, current = $currentAddr, end = $endAddr\n")
      sb.append(s"${endAddr.index - startAddr.index} bytes of AST, base = $currentAddr\n")
      while (!isAtEnd) {
        printTree()
        newLine()
      }
      sb.result
    }
  }

  class PositionSectionUnpickler extends SectionUnpickler[String]("Positions") {

    private val sb: StringBuilder = new StringBuilder

    def unpickle(reader: TastyReader, tastyName: NameTable): String = {
      sb.append(s" ${reader.endAddr.index - reader.currentAddr.index}")
      val spans = new PositionUnpickler(reader, tastyName).spans
      sb.append(s" position bytes:\n")
      val sorted = spans.toSeq.sortBy(_._1.index)
      for ((addr, pos) <- sorted) {
        sb.append(treeStr("%10d".format(addr.index)))
        sb.append(s": ${offsetToInt(pos.start)} .. ${pos.end}\n")
      }
      sb.result
    }
  }

  class CommentSectionUnpickler extends SectionUnpickler[String]("Comments") {

    private val sb: StringBuilder = new StringBuilder

    def unpickle(reader: TastyReader, tastyName: NameTable): String = {
      sb.append(s" ${reader.endAddr.index - reader.currentAddr.index}")
      val comments = new CommentUnpickler(reader).comments
      sb.append(s" comment bytes:\n")
      val sorted = comments.toSeq.sortBy(_._1.index)
      for ((addr, cmt) <- sorted) {
        sb.append(treeStr("%10d".format(addr.index)))
        sb.append(s": ${cmt.raw} (expanded = ${cmt.isExpanded})\n")
      }
      sb.result
    }
  }

  protected def nameStr(str: String): String = str
  protected def treeStr(str: String): String = str
  protected def lengthStr(str: String): String = str
}
