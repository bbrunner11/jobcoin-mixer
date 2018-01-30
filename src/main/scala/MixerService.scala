package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpUtils.sendJobCoinTransaction
import bb.mixer.MixerMain.{addressInToMixerIn, addressInToMixerOut, mixerInToAddressIn}
import scala.util.control.Breaks._

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }

}

case class MixerOutAddresses(fromAddress: String, addresses: Seq[String])
case class MixerResponse(payload: String)
case class MixFundsIn(fromAddress: String, mixerAddress: String, amount: String)
case class MixFundsOut(initatingAddress: String, mixerAddress: String, outAddresses: Seq[String], amount: Int, houseKeeps: Boolean) {
  override def toString = s" MixFundsOut(initAddres: $initatingAddress mixerAddress: $mixerAddress outAccounts: $outAddresses txAmount: $amount houseKeeps: $houseKeeps)"
}



class MixerService extends Actor with ActorLogging {

  val config = Configs
  val rand1 = new scala.util.Random

  def receive: Receive = {
    case mfi: MixFundsIn => {
      log.info(s"\nPrimary Account: ${mfi.fromAddress}\n\tMixerAddress: ${mfi.mixerAddress}\n\tAmount: ${mfi.amount}\n$mixerInToAddressIn")
    }
    case mfo: MixFundsOut => {
      doTheMix(mfo)
    }
    case moa: MixerOutAddresses => {
      storeOutAccounts(moa)
      log.info(s"\nPrimary Account: ${moa.fromAddress}\n\tMixerOutAccounts: ${moa.addresses.toString}")
      log.info(s"MixerInToAdressIn: $mixerInToAddressIn")
    }
    case _ => log.info("Akka sender not sending a valid message.")
  }

  private def storeOutAccounts(moa: MixerOutAddresses): Unit = {
    addressInToMixerOut.getOrElseUpdate(moa.fromAddress, moa.addresses) //right now, 1 unique address is assigned to a predefined set of out addresses
    log.info(addressInToMixerOut.toString)
  }

  private def doTheMix(mtf: MixFundsOut): Unit = {

    val txBuckets = 5 + rand1.nextInt(10) //split the amount into this many buckets(base + some random Int to 10
    val addressesOut = mtf.outAddresses
    val amount = mtf.amount

    /**
      * Split the transaction amount into increments
      * For each incremental amount, take an arbitrary .05 for the house, send the rest to the out address(es)
      * Only if the transaction to the out account is successful does the house take the vig
      * Otherwise the failed balance with vig intact stays in the mixer account and will get picked up by the next poller
      */

    val splitAmount = randSum(txBuckets, 0, amount).filter(_ != 0)

    splitAmount.foreach(randAmt => {
      val out = rand1.nextInt(addressesOut.size)
      val vig = if (randAmt < 10) 0 else Math.round(randAmt * .05) //since the mixer can only handle integer amounts, calc vig and round for values > 10
      log.info(s"${mtf.mixerAddress} sent ${randAmt - vig} to ${addressesOut(out)} by ${mtf.initatingAddress}") //send random amount to out accts
      log.info(s"${mtf.mixerAddress} sent transaction fee of $vig to ${config.houseVigAddress} by ${mtf.initatingAddress}")

      Thread.sleep(((rand1.nextDouble * config.mixerSleep.toDouble) + config.mixerSleep.toDouble).toLong) // randomize transaction timing

      if (sendJobCoinTransaction(mtf.mixerAddress, addressesOut(out), (randAmt - vig).toString).isSuccess && vig != 0) //can't send 0 amount
        sendJobCoinTransaction(mtf.mixerAddress, config.houseVigAddress, vig.toString)

    })

   }

  private def randSum(n: Int, min: Int, m: Int): List[Int] = { //TODO refactor this crap
    val rand = scala.util.Random

    val max = m - min * n
    val nums = new scala.collection.mutable.ListBuffer[Int]

    nums ++= nums.padTo(n, 0)

    if (max <= 0) throw new IllegalArgumentException

    for (i <- 1 until n - 1) {
      nums(i) = rand.nextInt(max)
    }

    val sorted = nums.sorted

    for (i <- 1 until sorted.length) {
      sorted(i - 1) = sorted(i) - sorted(i - 1) + min
    }

    sorted(n - 1) = max - sorted(n - 1) + min

    sorted.toList
  }

}
