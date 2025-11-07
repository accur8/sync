package a8.shared

import org.scalatest.GivenWhenThen
import org.scalatest.funsuite.AnyFunSuite
import a8.shared.SharedImports.canEqual.given

class SynchronizeTest extends AnyFunSuite with GivenWhenThen {

  case class ItemA(
    id: Int,
    name: String,
  )

  case class ItemB(
    correlationId: Int,
    description: String,
  )

  test("Synchonrize.asymetric") {

    import Synchronize.AsymetricAction

    val a =
      Vector(
        ItemA(1, "1"),
        ItemA(2, "2"),
      )

    val b =
      Vector(
        ItemB(2, "2"),
        ItemB(3, "3"),
      )


    def testRun(source: Iterable[ItemA], target: Iterable[ItemB], expected: Iterable[AsymetricAction[ItemA,ItemB]]): Unit = {
      val actual =
        Synchronize.asymetric[ItemA,ItemB,Int](
          source,
          target,
          _.id,
          _.correlationId
        )

      assert(actual == expected.toVector): @scala.annotation.nowarn

    }

    import AsymetricAction._
    testRun(
      a,
      b,
      Vector(
        Update(ItemA(2,"2"),ItemB(2,"2")),
        Delete(ItemB(3,"3")),
        Insert(ItemA(1,"1")),
      )
    )

  }

  test("Synchonrize.apply") {

    val a =
      Vector(
        ItemA(1, "1"),
        ItemA(2, "2"),
      )

    val b =
      Vector(
        ItemA(2, "2"),
        ItemA(3, "3"),
      )


    import Synchronize.Action
    import Action._
    def testRun(source: Vector[ItemA], target: Vector[ItemA], expected: Vector[Action[ItemA]]): Unit = {
      val actual =
        Synchronize[ItemA,Int](
          source,
          target,
          _.id,
        )

      assert(actual == expected): @scala.annotation.nowarn

    }

    testRun(
      a,
      b,
      Vector(
        Noop(ItemA(2,"2"),ItemA(2,"2")),
        Delete(ItemA(3,"3")),
        Insert(ItemA(1,"1")),
      )
    )

  }
}
