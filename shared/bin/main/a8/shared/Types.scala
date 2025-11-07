package a8.shared

import scala.quoted.{Expr, Quotes, Type}
import scala.quoted.*

object Types {


  inline def classNameWithGenerics[T]: String = ${ classNameWithGenericsImpl[T] }

  def classNameWithGenericsImpl[T: Type](using Quotes): Expr[String] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    def format(tpe: TypeRepr): String = tpe match {
      case AppliedType(tycon, args) =>
        s"${format(tycon)}[${args.map(format).mkString(", ")}]"
      case TypeRef(_, typeName) =>
        typeName
      case TermRef(_, termName) =>
        termName
      case _ =>
        tpe.show
    }
    Expr(format(tpe))
  }

}
