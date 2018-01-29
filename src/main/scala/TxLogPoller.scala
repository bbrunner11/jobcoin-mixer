package bb.mixer


import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{mixerInToAddressIn, primaryToMixerIn, primaryToMixerOut, primaryToLastActivity}
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

case class FlattenedTransactions(fromAddress: String, toAddress: String, timeStamp: String, amount: String)

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
    //    val allTransactionsPerUser = primaryToMixerOut.keys.map(x => getJobCoinTransactionsFor(x)).toList.map(r => parseResponse(r))
    //      .map(txs => {
    //        val validTransactions = txs.transactions.filter(f => f.fromAddress.isDefined && parseTimestamp(f.timestamp).compareTo(primaryToLastActivity(f.fromAddress.get)) >= 0)
    //          .map(ft => FlattenedTransactions(ft.fromAddress.get, ft.toAddress, ft.timestamp, ft.amount))
    //
    //        validTransactions//.filter(mixed => primaryToMixerIn.keySet.exists(_ == mixed.toAddress))
    //      }).flatten



//    val allMixerAddresses = mixerInToAddressIn.map { case (mixAddress, fromAddress) => getJobCoinTransactionsFor(mixAddress) }.toList.map(r => parseResponse(r))
//      .map(tx => (tx.balance, tx.transactions.find(_.fromAddress.isDefined) match {
//        case Some(from) => from.fromAddress.get //get ok here due to previous isDefined and pattern match
//        case None => "house account"
//      }))

    //TODO need to do proper string to number casting and error handling at some point!!!
    mixerInToAddressIn.update("test1", "test2")

    /**
      * Get all transactions for known mixers that do not have a zero balance. If we know there's a balance at a mixer address when this runs,
      * the timeStamp is irrelevant, the balance should be mixed and distributed to whoever is assigned to that mixer address
      *
      * Partition by the fromAddress, since if it's a mixer address with no fromAddress, the house gets to keep it :)
      * Filter the transactions where a fromAddress is defined but is also a mixer address since we don't care about mixer outgoing transactions
      */
    val allMixerAddresses = mixerInToAddressIn.map { case (mixAddress, fromAddress) => getJobCoinTransactionsFor(mixAddress) }.toList.map(r => parseResponse(r)).filter(_.balance != "0")
        .map(x => x.transactions.partition(_.fromAddress.isDefined)).map(m => (m._1.filter(n => mixerInToAddressIn.keySet.contains(n.toAddress)), m._2))
    //OwnerTransactions(validTransactions.head.fromAddress.get, txs.balance, validTransactions.head.fromAddress.get, validTransactions)
    //}).filter(ot => ot.toAccount == primaryToMixerIn.get(ot.owner))

    println(allMixerAddresses.mkString("\n"))


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
