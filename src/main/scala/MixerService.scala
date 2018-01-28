package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.MixerMain.{primaryToMixerIn, primaryToMixerOut}

object MixerService {
  def props(): Props = {
    Props(classOf[MixerService])
  }
}
case class MixThis(fromAddress: String, mixerAddress: String, amount: String)
case class MixerOutAccounts(primaryAccount: String, accounts: Seq[String])
case class MixerResponse(payload: String)




class MixerService extends Actor with ActorLogging {

  def receive: Receive = {
    case mt: MixThis => log.info(s"\nPrimary Account: ${mt.fromAddress}\n\tMixerAddress: ${mt.mixerAddress}\n\tAmount: ${mt.amount}\n$primaryToMixerIn")
    case moa: MixerOutAccounts => storeOutAccounts(moa);log.info(s"\nPrimary Account: ${moa.primaryAccount}\n\tMixerOutAccounts: ${moa.accounts.toString}")
    case _ => log.info("whatever")
  }

  def storeOutAccounts( moa: MixerOutAccounts): Unit = {
    primaryToMixerOut.update(moa.primaryAccount, moa.accounts) //TODO need to grow accounts list if key already exists
    log.info(primaryToMixerOut.toString)
  }

  def doTheMix(mt: MixThis): Unit = {
    //distribute a total amount into sub accounts

    val distIncrement = 10
    val acctsOut = primaryToMixerOut.get(mt.fromAddress).get //TODO fix this Option
    var amount = mt.amount.toDouble

    for (iter <- 1 to distIncrement) {
      val rand1 = new scala.util.Random
      val randDouble = rand1.nextDouble
      val combined = (amount / (distIncrement - iter)) * randDouble //somewhat normalize distribution based on stage of iteration

      val randInt = new scala.util.Random
      val out = randInt.nextInt(acctsOut.size)

      Thread.sleep(1000L) // TODO figure out a way to randomize the sleep
      if (amount == 0 || iter == 10)
        log.info(s"balance $amount sent to ${acctsOut(out)}\n") //send balance to out accts
      else {
        log.info(s"random $combined sent to ${acctsOut(out)}\n") //send random amount to out accts

      }
      amount -= combined

    }
  }

}
