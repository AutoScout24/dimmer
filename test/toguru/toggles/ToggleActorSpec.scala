package toguru.toggles

import akka.actor.Props
import akka.pattern.ask
import toguru.toggles.ToggleActor._

class ToggleActorSpec extends ActorSpec {

  trait ToggleActorSetup {
    def createActor(id: String, toggle: Option[Toggle] = None) = system.actorOf(Props(new ToggleActor(id, toggle)))
    val toggle        = Toggle("id", "name","description")
    val createCommand = CreateToggleCommand("name", "toggle description", Map("team" -> "Shared Services"))
    val setGlobalRolloutCommand = SetGlobalRolloutCommand(42)
  }

  "actor" should {
    "create toggle when receiving command in initial state" in new ToggleActorSetup {
      val actor = createActor("toggle-1")
      val response = await(actor ? createCommand)
      response mustBe CreateSucceeded("toggle-1")
    }

    "reject create command when toggle exists" in new ToggleActorSetup {
      val actor = createActor("toggle-2", Some(toggle))
      val response = await(actor ? createCommand)
      response mustBe ToggleAlreadyExists("toggle-2")
    }

    "create global rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor("toggle-3", Some(toggle))
      val response = await(actor ? setGlobalRolloutCommand)
      response mustBe Success

      val actorToggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
      actorToggle.rolloutPercentage mustBe Some(setGlobalRolloutCommand.percentage)
    }

    "update global rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor("toggle-3", Some(toggle.copy(rolloutPercentage = Some(55))))
      val response = await(actor ? setGlobalRolloutCommand)
      response mustBe Success

      val actorToggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
      actorToggle.rolloutPercentage mustBe Some(setGlobalRolloutCommand.percentage)
    }

    "reject set global rollout condition command when toggle does not exists" in new ToggleActorSetup {
      val actor = createActor("toggle-4")
      val response = await(actor ? setGlobalRolloutCommand)
      response mustBe ToggleDoesNotExist("toggle-4")
    }
  }
}
