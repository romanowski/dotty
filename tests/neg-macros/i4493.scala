import scala.quoted._
class Index[K]
object Index {
  inline def succ[K]: Unit = ${
    implicit val t: Type[K] = Type[K] // error
    '{new Index[K]} // error
  }
}
