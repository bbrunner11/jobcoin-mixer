package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions.sendJobCoinTransaction
import bb.mixer.MixerMain.{addressInToMixerIn, addressInToMixerOut}

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }
}
case class MixFundsIn(fromAddress: String, mixerAddress: String, amount: String)
case class MixFundsOut(initatingAddress: String, mixerAddress: String, outAccounts: Seq[String], amount: Double, houseKeeps: Boolean)
case class MixerOutAddresses(fromAddress: String, addresses: Seq[String])
case class MixerResponse(payload: String)

class MixerService extends Actor with ActorLogging {

  def receive: Receive = {
    case mfi: MixFundsIn => {
      val response = log.info(s"\nPrimary Account: ${mfi.fromAddress}\n\tMixerAddress: ${mfi.mixerAddress}\n\tAmount: ${mfi.amount}\n$addressInToMixerIn")
    }
    case mfo: List[MixFundsOut] => {
      mfo.foreach(tx => doTheMix(tx))
    }
    case moa: MixerOutAddresses => {
      storeOutAccounts(moa)
      log.info(s"\nPrimary Account: ${moa.fromAddress}\n\tMixerOutAccounts: ${moa.addresses.toString}")
      log.info(s"MixerInMap: $addressInToMixerIn")
    }
    case _ => log.info("whatever")
  }

  def storeOutAccounts( moa: MixerOutAddresses): Unit = {
    addressInToMixerOut.getOrElseUpdate(moa.fromAddress, moa.addresses) //right now, 1 unique address is assigned to a predefined set of out addresses
    log.info(addressInToMixerOut.toString)
  }

  def doTheMix(mtf: MixFundsOut): Unit = {



    //distribute a total amount into sub accounts
    Thread.sleep(1000L) // TODO figure out a way to randomize the sleep

    val distIncrement = 5 //TODO make this configurable
    val addressesOut = mtf.outAccounts
    var amount = mtf.amount

    for (iter <- 1 to distIncrement) {
      val rand1 = new scala.util.Random
      val randDouble = rand1.nextDouble
      val combined = (amount / (distIncrement - iter)) * randDouble //somewhat normalize distribution based on stage of iteration

      val randInt = new scala.util.Random
      val out = randInt.nextInt(addressesOut.size)  //random index to pick out accounts from Seq


      if (amount == 0 || iter == distIncrement) {
        log.info(s"${mtf.mixerAddress} sent balance $amount to ${addressesOut(out)} for ${mtf.initatingAddress}") //send balance to out accts
        sendJobCoinTransaction(mtf.mixerAddress, addressesOut(out), amount.toString)
      }
      else {
        log.info(s"${mtf.mixerAddress} sent random $combined to ${addressesOut(out)} for ${mtf.initatingAddress}") //send random amount to out accts
        sendJobCoinTransaction(mtf.mixerAddress, addressesOut(out), combined.toString)
      }
      amount -= combined

    }
  }

}
