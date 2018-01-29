package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions.sendJobCoinTransaction
import bb.mixer.MixerMain.{primaryToMixerIn, primaryToMixerOut}
import bb.mixer.HttpTransactions._

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }
}
case class MixThis(fromAddress: String, mixerAddress: String, amount: String)
case class MixerOutAddresses(primaryAddress: String, addresses: Seq[String])
case class MixerResponse(payload: String)

class MixerService extends Actor with ActorLogging {

  def receive: Receive = {
    case mt: MixThis => {
      val response = log.info(s"\nPrimary Account: ${mt.fromAddress}\n\tMixerAddress: ${mt.mixerAddress}\n\tAmount: ${mt.amount}\n$primaryToMixerIn")
    }
    case moa: MixerOutAddresses => storeOutAccounts(moa);log.info(s"\nPrimary Account: ${moa.primaryAddress}\n\tMixerOutAccounts: ${moa.addresses.toString}")
    case _ => log.info("whatever")
  }

  def storeOutAccounts( moa: MixerOutAddresses): Unit = {
    primaryToMixerOut.update(moa.primaryAddress, moa.addresses) //TODO need to grow accounts list if key already exists
    log.info(primaryToMixerOut.toString)
  }

  def doTheMix(mt: MixThis): Unit = {
    //distribute a total amount into sub accounts

    Thread.sleep(1000L) // TODO figure out a way to randomize the sleep

    val distIncrement = 10
    val addressesOut = primaryToMixerOut.get(mt.fromAddress).get //TODO fix this Option
    var amount = mt.amount.toDouble

    for (iter <- 1 to distIncrement) {
      val rand1 = new scala.util.Random
      val randDouble = rand1.nextDouble
      val combined = (amount / (distIncrement - iter)) * randDouble //somewhat normalize distribution based on stage of iteration

      val randInt = new scala.util.Random
      val out = randInt.nextInt(addressesOut.size)


      if (amount == 0 || iter == 10) {
        log.info(s"balance $amount sent to ${addressesOut(out)}\n") //send balance to out accts
        sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, amount.toString)
      }
      else {
        log.info(s"random $combined sent to ${addressesOut(out)}\n") //send random amount to out accts
        sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, combined.toString)
      }
      amount -= combined

    }
  }

}
