import scala.quoted._
import scala.quoted.staging._
import scala.reflect.ClassTag

object Test {
  given Toolbox = Toolbox.make(this.getClass.getClassLoader)
  def main(args: Array[String]): Unit = withQuoteContext {
    val '[List[Int]] = Type[List[Int]]

    Type[List[Int]] match
      case '[List[int]] =>
        println(Type.show[int])
        println()

    Type[Int => Double] match
      case  '[Function1[t1, r]] =>
        println(Type.show[t1])
        println(Type.show[r])
        println()

    Type[(Int => Short) => Double] match
      case '[Function1[Function1[t1, r0], r]] =>
        println(Type.show[t1])
        println(Type.show[r0])
        println(Type.show[r])

  }
}
