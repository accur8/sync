package examples

import a8.shared.SharedImports._
import a8.sync._
import a8.sync.DataValidation._

/**
 * Example of data validation and cleansing during sync operations
 */
object DataValidationExample extends App {

  case class CustomerRecord(
    id: Long,
    email: String,
    name: String,
    phone: Option[String],
    zipCode: String,
    creditLimit: BigDecimal
  )

  // Sample data with various validation issues
  val rawData = List(
    CustomerRecord(1, "john@example.com", "John Doe", Some("555-1234"), "12345", 10000),
    CustomerRecord(2, "JANE@EXAMPLE.COM", "Jane Smith with a very long name that exceeds database limits", Some("invalid-phone"), "123", 25000),
    CustomerRecord(3, "invalid-email", "Bob Johnson", None, "12345-6789", -500),
    CustomerRecord(4, "alice@example.com", "", Some("555-5678"), "54321", 15000),
    CustomerRecord(5, "alice@example.com", "Alice Duplicate", Some("555-9999"), "11111", 20000)
  )

  // Define validation rules
  val validationRules = ValidationRules[CustomerRecord](
    rules = Seq(
      // Email validation
      FieldRule("email", 
        validator = (email: String) => email.contains("@") && email.length <= 100,
        errorMessage = "Invalid email format",
        transform = (email: String) => email.toLowerCase.trim
      ),
      
      // Name validation
      FieldRule("name",
        validator = (name: String) => name.nonEmpty && name.length <= 50,
        errorMessage = "Name is required and must be <= 50 characters",
        transform = (name: String) => name.trim match {
          case n if n.length > 50 => n.take(47) + "..."
          case n => n
        }
      ),
      
      // Phone validation
      FieldRule("phone",
        validator = (phone: Option[String]) => phone.forall(_.matches("\\d{3}-\\d{4}")),
        errorMessage = "Phone must be in format XXX-XXXX",
        transform = (phone: Option[String]) => phone.map(_.replaceAll("[^0-9]", "")) match {
          case Some(digits) if digits.length >= 7 => 
            Some(s"${digits.take(3)}-${digits.slice(3, 7)}")
          case _ => None
        }
      ),
      
      // Zip code validation
      FieldRule("zipCode",
        validator = (zip: String) => zip.matches("\\d{5}"),
        errorMessage = "Zip code must be 5 digits",
        transform = (zip: String) => zip.replaceAll("[^0-9]", "").take(5).padTo(5, '0')
      ),
      
      // Credit limit validation
      FieldRule("creditLimit",
        validator = (limit: BigDecimal) => limit >= 0 && limit <= 100000,
        errorMessage = "Credit limit must be between 0 and 100,000",
        transform = (limit: BigDecimal) => limit.max(0).min(100000)
      )
    ),
    
    // Record-level validation
    recordValidator = { record =>
      // Check for duplicate emails
      val duplicateEmails = rawData.groupBy(_.email).filter(_._2.size > 1).keys.toSet
      if (duplicateEmails.contains(record.email)) {
        Left(s"Duplicate email found: ${record.email}")
      } else {
        Right(record)
      }
    }
  )

  // Process data with validation
  println("Processing customer records with validation...\n")

  val validator = DataValidator(validationRules)
  val results = rawData.map { record =>
    (record, validator.validate(record))
  }

  // Display results
  results.foreach {
    case (original, Right(validated)) =>
      if (original != validated) {
        println(s"✓ Record ${original.id} transformed:")
        println(s"  Original: $original")
        println(s"  Validated: $validated")
      } else {
        println(s"✓ Record ${original.id} passed validation unchanged")
      }
      
    case (original, Left(errors)) =>
      println(s"✗ Record ${original.id} failed validation:")
      println(s"  Original: $original")
      println(s"  Errors: ${errors.mkString(", ")}")
  }

  // Summary statistics
  val (valid, invalid) = results.partition(_._2.isRight)
  println(s"\nValidation Summary:")
  println(s"  Total records: ${results.size}")
  println(s"  Valid records: ${valid.size}")
  println(s"  Invalid records: ${invalid.size}")

  // Example: Process only valid records
  val validRecords = results.collect {
    case (_, Right(validated)) => validated
  }

  println(s"\nReady to sync ${validRecords.size} valid records to target database")

  // Example: Generate validation report
  val validationReport = ValidationReport(
    totalRecords = rawData.size,
    validRecords = valid.size,
    invalidRecords = invalid.size,
    transformedRecords = valid.count { case (orig, Right(validated)) => orig != validated },
    errorsByType = invalid.flatMap(_._2.left.toOption).flatten.groupBy(identity).map {
      case (error, list) => (error, list.size)
    }
  )

  println(s"\nDetailed Validation Report:")
  println(s"  Records transformed: ${validationReport.transformedRecords}")
  println(s"  Error breakdown:")
  validationReport.errorsByType.foreach { case (error, count) =>
    println(s"    - $error: $count occurrences")
  }
}

// Supporting classes
case class ValidationRules[T](
  rules: Seq[FieldRule[_]],
  recordValidator: T => Either[String, T] = (t: T) => Right(t)
)

case class FieldRule[T](
  fieldName: String,
  validator: T => Boolean,
  errorMessage: String,
  transform: T => T
)

case class DataValidator[T](rules: ValidationRules[T]) {
  def validate(record: T): Either[List[String], T] = {
    // Simplified validation logic
    // In real implementation, would use reflection or macros
    Right(record) // Placeholder
  }
}

case class ValidationReport(
  totalRecords: Int,
  validRecords: Int,
  invalidRecords: Int,
  transformedRecords: Int,
  errorsByType: Map[String, Int]
)