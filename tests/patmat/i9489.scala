import scala.quoted._

def summonTypedType[T : Type](using QuoteContext): String = Type[T] match {
  case '[Boolean] => "Boolean"
  case '[Byte] => "Byte"
  case _ => "Other"
}
