package bb.mixer

import scalaj.http._

object HttpTransactions {

  def getAllJobCoinTransactions: HttpResponse[String] = Http("http://jobcoin.gemini.com/puppy/api/transactions").asString

  def getJobCoinTransactionsFor(address: String): HttpResponse[String] = Http(s"http://jobcoin.gemini.com/puppy/api/addresses/$address").asString

  def sendJobCoinTransaction(fromAddress: String, toAddress: String, amount: String): HttpResponse[String] = {
    val x = Http("http://jobcoin.gemini.com/puppy/api/transactions")
      .postForm(Seq("fromAddress" -> fromAddress, "toAddress" -> toAddress, "amount" -> amount)).asString

    println("HTTP Response: "+x) //TODO Need to handle reponse codes from these requests
    x
  }
}
