package examples

import a8.shared.SharedImports._
import a8.shared.json.JsonCodec
import a8.shared.json.ast._
import a8.sync.qubes.QubesApiClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * Example of integrating with external JSON APIs
 */
object JsonApiIntegration extends App {

  // Define data models with automatic JSON codec derivation
  case class Product(
    id: String,
    name: String,
    price: BigDecimal,
    category: String,
    inStock: Boolean
  )

  case class Order(
    orderId: String,
    customerId: String,
    products: List[OrderItem],
    totalAmount: BigDecimal,
    status: String
  )

  case class OrderItem(
    productId: String,
    quantity: Int,
    unitPrice: BigDecimal
  )

  // Automatic codec generation
  implicit val productCodec: JsonCodec[Product] = JsonCodec.caseCodec[Product]
  implicit val orderItemCodec: JsonCodec[OrderItem] = JsonCodec.caseCodec[OrderItem]
  implicit val orderCodec: JsonCodec[Order] = JsonCodec.caseCodec[Order]

  // Configure API client
  val apiClient = QubesApiClient(
    baseUrl = "https://api.example.com",
    authToken = sys.env.getOrElse("API_TOKEN", "demo-token"),
    timeout = 30.seconds
  )

  // Example 1: Fetch and process products
  println("Fetching products from API...")
  val productsFuture = apiClient.get[List[Product]]("/products")
  
  val products = Await.result(productsFuture, 60.seconds)
  println(s"Retrieved ${products.size} products")
  
  // Group products by category
  val productsByCategory = products.groupBy(_.category)
  productsByCategory.foreach { case (category, prods) =>
    println(s"  $category: ${prods.size} products")
  }

  // Example 2: Create a new order
  val newOrder = Order(
    orderId = s"ORD-${System.currentTimeMillis}",
    customerId = "CUST-12345",
    products = List(
      OrderItem("PROD-001", 2, 29.99),
      OrderItem("PROD-002", 1, 49.99)
    ),
    totalAmount = 109.97,
    status = "pending"
  )

  println(s"\nCreating new order: ${newOrder.orderId}")
  val createOrderFuture = apiClient.post[Order]("/orders", newOrder)
  
  try {
    val createdOrder = Await.result(createOrderFuture, 30.seconds)
    println(s"Order created successfully: ${createdOrder.orderId}")
  } catch {
    case e: Exception =>
      println(s"Failed to create order: ${e.getMessage}")
  }

  // Example 3: Custom JSON handling
  val customJson = json"""
    {
      "query": {
        "category": "electronics",
        "priceRange": {
          "min": 10,
          "max": 500
        }
      },
      "sort": "price",
      "limit": 20
    }
  """

  println("\nExecuting custom search query...")
  val searchFuture = apiClient.post[List[Product]]("/products/search", customJson)
  val searchResults = Await.result(searchFuture, 30.seconds)
  println(s"Found ${searchResults.size} products matching criteria")
  
  // Example 4: Error handling with partial results
  case class ApiResponse[T](
    success: Boolean,
    data: Option[T],
    error: Option[String]
  )
  
  implicit def apiResponseCodec[T: JsonCodec]: JsonCodec[ApiResponse[T]] = 
    JsonCodec.caseCodec[ApiResponse[T]]

  val batchRequest = List("PROD-001", "PROD-002", "PROD-999")
  val batchFuture = apiClient.post[ApiResponse[List[Product]]](
    "/products/batch",
    Json.obj("ids" -> batchRequest.toJson)
  )

  Await.result(batchFuture, 30.seconds) match {
    case ApiResponse(true, Some(products), _) =>
      println(s"\nBatch request successful: ${products.size} products found")
    case ApiResponse(false, _, Some(error)) =>
      println(s"\nBatch request failed: $error")
    case _ =>
      println("\nUnexpected response format")
  }
}