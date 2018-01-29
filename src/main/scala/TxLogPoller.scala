package bb.mixer


import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{primaryToMixerIn, primaryToMixerOut, primaryToLastActivity}
import spray.json._
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat

import scalaj.http.HttpResponse


trait JsonSupport2 extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val transaction = jsonFormat4(Transaction)
  implicit val transactions = jsonFormat2(Transactions)
}

object TxLogPoller {
  def props(): Props = {
    Props(classOf[TxLogPoller])
  }
}

case class Transaction(timestamp: String, fromAddress: Option[String], toAddress: String, amount: String)

case class Transactions(balance: String, transactions: List[Transaction])

case class OwnerTransactions(owner: String, balance: String, toAccount: String, transaction: List[Transaction])


class TxLogPoller extends Actor with ActorLogging with JsonSupport2 {

  val now = Calendar.getInstance.getTime

  val formatDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

  //primaryToMixerIn.update("test1", "in1")

  def receive: Receive = {
    case "poll" if (primaryToMixerOut.nonEmpty) => processTxs
  }

  def processTxs = {
    /**
      * filter out transactions without a valid fromAddress since there is no way to correlate to a valid mixerOut addresses
      * filter out any transactions prior to the initial call to /startmixer or the last run of the poller
      * move fromAddress, toAddress and balance to the same level to make it easier to filter later
      */
    val allTransactionsPerUser = primaryToMixerOut.keys.map(x => getJobCoinTransactionsFor(x)).toList.map(r => parseResponse(r))
      .map(txs => {
        val validTransactions = txs.transactions.filter(f => f.fromAddress.isDefined && parseTimestamp(f.timestamp).compareTo(primaryToLastActivity(f.fromAddress.get)) >= 0)
        OwnerTransactions(validTransactions.head.fromAddress.get, txs.balance, validTransactions.head.fromAddress.get, validTransactions)
      }).filter(ot => ot.toAccount == primaryToMixerIn.get(ot.owner))

    println(allTransactionsPerUser.mkString)


    //val zz = allTransactionsPerUser.map(txs => OwnerTransactions(txs.transactions.head.fromAddress.get, txs.balance, txs.transactions))
    //val mixerTransactions = allTransactionsPerUser.flatMap(f => f.transactions.filter(ts => parseTimestamp(ts.timestamp).compareTo(now) > 0)) //only get txs since the last poll

    primaryToMixerIn.update("Alice", "Bob")

    //    val mixerTransactions = allTransactionsPerUser.map(f => f.transactions).map(h => h.groupBy(g => (g.fromAddress, f.balance))).flatten
    //      .toMap.collect { case ((Some(k),k2), v) => (k, k2) -> v} //no from address, can't roll up to a mix out address TODO derive primary address?
    //      .map(f => f._1 -> f._2.filter(t => parseTimestamp(t.timestamp).compareTo(primaryToLastActivity(t.fromAddress.get)) <= 0))


    //    val txSinceLastPoll = mixerTransactions.map(f => f._2.filter(t => parseTimestamp(t.timestamp)
    //      .compareTo(primaryToLastActivity(t.fromAddress.get)) >= 0)).map(_.groupBy(_.fromAddress)).flatten
    //      .toMap.collect { case ((Some(k),k2), v) => (k, k2) -> v} //make a map of all transactions per address that wants to mix and has a tx timestamp delta

    //val txSinceLastPoll = mixerTransactions.flatMap(x => x.filter(z => parseTimestamp(z.timestamp).compareTo(now) < 0)) //all users that have a mixer address and have added funds to that address since last poll

    //println("SSSSS   " + mixerTransactions.mkString("\n"))

    // log.info(parseResponse(response).toString)
    // else log.info("blah")
    //context.actorOf(MixerService.props()) ! MixThis()

  }


  def parseResponse(response: HttpResponse[String]): Transactions = {
    response.body.parseJson.convertTo[Transactions]

  }

  def parseTimestamp(dateStr: String): Date = {
    formatDate.parse(dateStr)
  }
}
