package a8.shared.jdbcf

trait RowReaderTuples {

  implicit def tuple1[A1:RowReader]: RowReader[(A1)] =
    new RowReader[(A1)] {
      override def rawRead(row: Row, index: Int): ((A1), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        (a1) -> offset
      }
    }

  implicit def tuple2[A1:RowReader,A2:RowReader]: RowReader[(A1,A2)] =
    new RowReader[(A1,A2)] {
      override def rawRead(row: Row, index: Int): ((A1,A2), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        (a1,a2) -> offset
      }
    }

  implicit def tuple3[A1:RowReader,A2:RowReader,A3:RowReader]: RowReader[(A1,A2,A3)] =
    new RowReader[(A1,A2,A3)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        (a1,a2,a3) -> offset
      }
    }

  implicit def tuple4[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader]: RowReader[(A1,A2,A3,A4)] =
    new RowReader[(A1,A2,A3,A4)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        (a1,a2,a3,a4) -> offset
      }
    }

  implicit def tuple5[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader]: RowReader[(A1,A2,A3,A4,A5)] =
    new RowReader[(A1,A2,A3,A4,A5)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        (a1,a2,a3,a4,a5) -> offset
      }
    }

  implicit def tuple6[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6)] =
    new RowReader[(A1,A2,A3,A4,A5,A6)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        (a1,a2,a3,a4,a5,a6) -> offset
      }
    }

  implicit def tuple7[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        (a1,a2,a3,a4,a5,a6,a7) -> offset
      }
    }

  implicit def tuple8[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        (a1,a2,a3,a4,a5,a6,a7,a8) -> offset
      }
    }

  implicit def tuple9[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9) -> offset
      }
    }

  implicit def tuple10[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10) -> offset
      }
    }

  implicit def tuple11[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11) -> offset
      }
    }

  implicit def tuple12[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12) -> offset
      }
    }

  implicit def tuple13[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13) -> offset
      }
    }

  implicit def tuple14[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14) -> offset
      }
    }

  implicit def tuple15[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15) -> offset
      }
    }

  implicit def tuple16[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16) -> offset
      }
    }

  implicit def tuple17[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader,A17:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        val (a17, a17o) = RowReader[A17].rawRead(row, offset+index)
        offset += a17o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17) -> offset
      }
    }

  implicit def tuple18[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader,A17:RowReader,A18:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        val (a17, a17o) = RowReader[A17].rawRead(row, offset+index)
        offset += a17o
        val (a18, a18o) = RowReader[A18].rawRead(row, offset+index)
        offset += a18o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18) -> offset
      }
    }

  implicit def tuple19[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader,A17:RowReader,A18:RowReader,A19:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        val (a17, a17o) = RowReader[A17].rawRead(row, offset+index)
        offset += a17o
        val (a18, a18o) = RowReader[A18].rawRead(row, offset+index)
        offset += a18o
        val (a19, a19o) = RowReader[A19].rawRead(row, offset+index)
        offset += a19o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19) -> offset
      }
    }

  implicit def tuple20[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader,A17:RowReader,A18:RowReader,A19:RowReader,A20:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        val (a17, a17o) = RowReader[A17].rawRead(row, offset+index)
        offset += a17o
        val (a18, a18o) = RowReader[A18].rawRead(row, offset+index)
        offset += a18o
        val (a19, a19o) = RowReader[A19].rawRead(row, offset+index)
        offset += a19o
        val (a20, a20o) = RowReader[A20].rawRead(row, offset+index)
        offset += a20o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19,a20) -> offset
      }
    }

  implicit def tuple21[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader,A17:RowReader,A18:RowReader,A19:RowReader,A20:RowReader,A21:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20,A21)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20,A21)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20,A21), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        val (a17, a17o) = RowReader[A17].rawRead(row, offset+index)
        offset += a17o
        val (a18, a18o) = RowReader[A18].rawRead(row, offset+index)
        offset += a18o
        val (a19, a19o) = RowReader[A19].rawRead(row, offset+index)
        offset += a19o
        val (a20, a20o) = RowReader[A20].rawRead(row, offset+index)
        offset += a20o
        val (a21, a21o) = RowReader[A21].rawRead(row, offset+index)
        offset += a21o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19,a20,a21) -> offset
      }
    }

  implicit def tuple22[A1:RowReader,A2:RowReader,A3:RowReader,A4:RowReader,A5:RowReader,A6:RowReader,A7:RowReader,A8:RowReader,A9:RowReader,A10:RowReader,A11:RowReader,A12:RowReader,A13:RowReader,A14:RowReader,A15:RowReader,A16:RowReader,A17:RowReader,A18:RowReader,A19:RowReader,A20:RowReader,A21:RowReader,A22:RowReader]: RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20,A21,A22)] =
    new RowReader[(A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20,A21,A22)] {
      override def rawRead(row: Row, index: Int): ((A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12,A13,A14,A15,A16,A17,A18,A19,A20,A21,A22), Int) = {
        var offset = 0
        val (a1, a1o) = RowReader[A1].rawRead(row, offset+index)
        offset += a1o
        val (a2, a2o) = RowReader[A2].rawRead(row, offset+index)
        offset += a2o
        val (a3, a3o) = RowReader[A3].rawRead(row, offset+index)
        offset += a3o
        val (a4, a4o) = RowReader[A4].rawRead(row, offset+index)
        offset += a4o
        val (a5, a5o) = RowReader[A5].rawRead(row, offset+index)
        offset += a5o
        val (a6, a6o) = RowReader[A6].rawRead(row, offset+index)
        offset += a6o
        val (a7, a7o) = RowReader[A7].rawRead(row, offset+index)
        offset += a7o
        val (a8, a8o) = RowReader[A8].rawRead(row, offset+index)
        offset += a8o
        val (a9, a9o) = RowReader[A9].rawRead(row, offset+index)
        offset += a9o
        val (a10, a10o) = RowReader[A10].rawRead(row, offset+index)
        offset += a10o
        val (a11, a11o) = RowReader[A11].rawRead(row, offset+index)
        offset += a11o
        val (a12, a12o) = RowReader[A12].rawRead(row, offset+index)
        offset += a12o
        val (a13, a13o) = RowReader[A13].rawRead(row, offset+index)
        offset += a13o
        val (a14, a14o) = RowReader[A14].rawRead(row, offset+index)
        offset += a14o
        val (a15, a15o) = RowReader[A15].rawRead(row, offset+index)
        offset += a15o
        val (a16, a16o) = RowReader[A16].rawRead(row, offset+index)
        offset += a16o
        val (a17, a17o) = RowReader[A17].rawRead(row, offset+index)
        offset += a17o
        val (a18, a18o) = RowReader[A18].rawRead(row, offset+index)
        offset += a18o
        val (a19, a19o) = RowReader[A19].rawRead(row, offset+index)
        offset += a19o
        val (a20, a20o) = RowReader[A20].rawRead(row, offset+index)
        offset += a20o
        val (a21, a21o) = RowReader[A21].rawRead(row, offset+index)
        offset += a21o
        val (a22, a22o) = RowReader[A22].rawRead(row, offset+index)
        offset += a22o
        (a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19,a20,a21,a22) -> offset
      }
    }

}
