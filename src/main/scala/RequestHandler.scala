
package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpUtils._
import bb.mixer.MixerMain.{addressInToMixerOut, mixerInToAddressIn}

object RequestHandler {
  def props(): Props = {
    Props(classOf[RequestHandler])
  }
}

case class StatusRequest(address: String)

case class Response(payload: String)

case class Error(error: String)

class RequestHandler extends Actor with ActorLogging with JsonSupport {

  var incMixer = 0 //increment int for mixer addresses

  def receive: Receive = {

    case moa: MixerOutAddresses => {
      addressInToMixerOut.get(moa.fromAddress) match {
        case Some(_) => {
          sender ! Error("You already have alternate addresses associated with this address.  Just send some funds to your mixer.") //limit one set of alternate addresses per primary address
        }
        case None => {
          storeOutAccounts(moa) //map user address to their mixer out accounts

          incMixer += 1
          mixerInToAddressIn.update(s"${moa.fromAddress}_mixer$incMixer", moa.fromAddress)

          sender ! Response(
            s"""Sent your info to the Mixer.  Your mixer address is '${moa.fromAddress}_mixer$incMixer'.
               |Please send the funds you wish mixed to that address.
               |WRITE YOUR MIXER ADDRESS DOWN!  It's unique and tied to your account ONLY.
               |
               |NOTE:
               |The mixer is only able to process *integer numbers*
               |ie, (those without a decimal place) at this time.
             """.stripMargin)
        }
      }
    }
    case mt: MixFundsIn => {
      addressInToMixerOut.get(mt.fromAddress) match { //check that the user has notified mixer of out addresses
        case Some(_) => {
          sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, mt.amount) match { //send to Gemini API @ mixer address
            case r if (r.code) == 200 => {
              context.actorOf(MixerService.props()) ! MixFundsIn(mt.fromAddress, mt.mixerAddress, mt.amount) //TODO get rid of this, the poller will pick up the transaction
              sender ! Response(s"Sent your info to the Mixer. Your mix will be started momentarily. Thanks")
            }
            case r if (r.code) == 422 => {
              sender ! Error(s" Error. Insufficient funds in account ${mt.fromAddress}. Unable to transfer to the mixer.")
            }
            case _ => sender ! Error("Something went wrong.")
          }
        }
        case None => {
          sender ! Error(s"You have not notified the mixer service of the alternate address(es) to use in the mix.")
        }
      }
    }
    case sr: StatusRequest => {
      val result = getJobCoinTransactionsFor(sr.address)
      sender ! Response(s"Balance of ${sr.address}: ${parseResponse(result).balance}")
    }

    case _ => println("Error, not sure what."); Error

  }

  private def storeOutAccounts(moa: MixerOutAddresses): Unit = {
    addressInToMixerOut.getOrElseUpdate(moa.fromAddress, moa.addresses) //right now, 1 unique address is assigned to a predefined set of out addresses
    log.info(addressInToMixerOut.toString)
  }

}
