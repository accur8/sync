package a8.shared

import a8.shared.json.{JsonCodec, JsonReadOptions, ReadError, ast}

object SingleArgConstructor {

  abstract class Companion[A : JsonCodec, B <: Product] extends SingleArgConstructor[A,B]  {

    def apply(a: A): B
    def construct(a: A): B = apply(a)
    def deconstruct(b: B): A = b.productElement(0).asInstanceOf[A]
    implicit val jsonCodec: JsonCodec[B] = {
      val jsonCodecA = implicitly[JsonCodec[A]]
      new JsonCodec[B] {
        override def write(b: B): ast.JsVal =
          jsonCodecA.write(deconstruct(b))

        override def read(doc: ast.JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, B] =
          jsonCodecA.read(doc).map(construct)

      }
    }
  }

  def create[A,B](
    constructFn: A=>B,
    deconstructFn: B=>A
  ): SingleArgConstructor[A,B] =
    new SingleArgConstructor[A,B] {
      override def construct(a: A): B = constructFn(a)
      override def deconstruct(b: B): A = deconstructFn(b)
    }

  implicit val longToBigDecimal =
    create[Long,BigDecimal](
      BigDecimal.valueOf,
      _.toLong,
    )

  implicit val shortToBigDecimal =
    create[Short,BigDecimal](
      n => BigDecimal.valueOf(n),
      _.toShort,
    )

  implicit val intToBigDecimal =
    create[Int,BigDecimal](
      i => BigDecimal.valueOf(i),
      _.toInt,
    )

  implicit val bigIntToBigDecimal =
    create[BigInt,BigDecimal](
      bi => BigDecimal(bi),
      _.toBigInt,
    )

}


trait SingleArgConstructor[A,B] {
  def construct(a: A): B
  def deconstruct(b: B): A
}
