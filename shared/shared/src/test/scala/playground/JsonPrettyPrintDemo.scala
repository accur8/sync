package playground


import a8.shared.SharedImports._
import a8.shared.Chord
import a8.shared.Chord._
import a8.shared.json.ast.JsBool

object JsonPrettyPrintDemo {


  def main(args: Array[String]): Unit = {

    val jv = json.unsafeParse("""[{"hello":1,"abc":[1,2,3,4,5]}]""")
//    val jv = json.unsafeParse("""{"hello":1,"abc":1}""")

    println(jv.prettyJson)

    println(
      ch"hello${Chord.str("world")}".toString
    )

    json.unsafeParse("true") match {
      case JsBool(true) =>
        println("true")
      case JsBool(false) =>
        println("false")
      case x =>
        println("x -- " + x)
    }

    json.unsafeParse("true") match {
      case JsBool(b) =>
        println(b)
      case x =>
        println("x -- " + x)
    }

  }
}
