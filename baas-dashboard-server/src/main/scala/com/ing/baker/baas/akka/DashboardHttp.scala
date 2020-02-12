package com.ing.baker.baas.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.stream.Materializer
import cats.effect.IO
import com.ing.baker.baas.dashboard.BakeryApi
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.syntax._
import io.circe.{Encoder, JsonObject}

import scala.concurrent.Future

object DashboardHttp {

  def run(bakeryApi: BakeryApi)(host: String, port: Int)(implicit system: ActorSystem, mat: Materializer): Future[Http.ServerBinding] = {
    import system.dispatcher
    val server = new DashboardHttp(bakeryApi)
    println(port)
    Http().bindAndHandle(server.route, host, port)
  }
}

class DashboardHttp(bakeryApi: BakeryApi)(implicit system: ActorSystem, mat: Materializer) extends ErrorAccumulatingCirceSupport {

  private def route: Route = concat(pathPrefix("api" / "v3")(concat(health, listRecipes, getRecipe, listInstances,
    getRecipeInstance, listInstanceEvents)), public)

  private def health: Route = pathPrefix("health")(get(complete(StatusCodes.OK)))

  case class RecipeInfo(name: String, recipeId: String, creationTime: Long)

  case class ListRecipesResponse(recipes: List[RecipeInfo])

  def completeJson[A](a: IO[A])(implicit encoder: Encoder[A]): StandardRoute =
    complete(a.map(x => JsonObject("data" -> x.asJson).asJson).unsafeToFuture())

  private def listRecipes: Route = get(pathPrefix("recipes")(completeJson(bakeryApi.listRecipes)))

  private def getRecipe: Route = get(pathPrefix("recipes" / Segment) { recipeId =>
    completeJson(bakeryApi.getRecipe(recipeId))
  })

  private def listInstances: Route = get(path("recipes" / Segment) { recipeId =>
    path("instances")(completeJson(bakeryApi.listInstances(recipeId)))
  })

  private def getRecipeInstance: Route = get(path("recipes" / Segment) { recipeId =>
    path("instances" / Segment) { recipeInstanceId =>
      completeJson(bakeryApi.getRecipeInstance(recipeId, recipeInstanceId))
    }
  })

  private def listInstanceEvents: Route = get(path("recipes" / Segment) { recipeId =>
    path("instances" / Segment) { recipeInstanceId =>
      path("events")(completeJson(bakeryApi.listEvents(recipeId, recipeInstanceId)))
    }
  })

  private def public: Route = pathPrefix("dashboard") {
    concat(
      getFromResourceDirectory("public"),
      get(getFromResource("public/index.html")))
  }
}