package toguru

import akka.actor.{ActorContext, ActorRef}

package object toggles {

  type ActorInitializer = (ActorContext, ActorRef) => Unit

}
