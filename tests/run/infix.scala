import annotation.{infix, targetName}
object Test extends App {

  case class Rational(n: Int, d: Int) {
    @infix def + (that: Rational) =
      Rational(this.n * that.d + that.n * this.d, this.d * that.d)
    @infix @targetName("multiply") def * (that: Rational) =
      Rational(this.n * that.n, this.d * that.d)
  }

  val r1 = Rational(1,2)
  val r2 = Rational(2,3)
  println(r1 * r2)
  println(r1 + r2)
}