package io.floodplain.quarkus.example.io.floodplain.quarkus.example
import io.floodplain.elasticsearch.elasticSearchConfig
import io.floodplain.elasticsearch.elasticSearchSink
import io.floodplain.kotlindsl.*
import io.floodplain.kotlindsl.message.IMessage
import io.floodplain.mongodb.mongoConfig
import io.floodplain.mongodb.mongoSink
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain

private val logger = mu.KotlinLogging.logger {}

@QuarkusMain
class FilmsToMongo: QuarkusApplication {
    override fun run(vararg args: String?): Int {
        streams {
            val postgresConfig = postgresSourceConfig("mypostgres", "localhost", 5432, "postgres", "mysecretpassword", "dvdrental", "public")
            val elasticConfig = elasticSearchConfig("elastic", "http://localhost:9200")
            val mongoConfig = mongoConfig(
                    "mongosink",
                    "mongodb://localhost:27017",
                    "@mongodump"
            )
            listOf(
                    postgresSource("film",postgresConfig) {
                        // Clear the last_update field, it makes no sense in a denormalized situation
                        set { _, film, _ ->
                            film["last_update"] = null; film
                        }
                        // Join with something that also uses the film_id key space.
                        // optional = true so films without any actors (animation?) will propagate
                        // multiple = true we're not joining with something that actually 'has' a film id
                        // we are joining with something that is grouped by film_id
                        joinGrouped(optional = true) {

                            postgresSource("film_actor",postgresConfig) {
                                joinRemote({ msg -> "${msg["actor_id"]}" }, false) {
                                    postgresSource("actor",postgresConfig) {
                                    }
                                }
                                // copy the first_name, last_name and actor_id to the film_actor message, drop the last update
                                set { _, actor_film, actor ->
                                    actor_film["last_name"] = actor["last_name"]
                                    actor_film["first_name"] = actor["first_name"]
                                    actor_film["actor_id"] = actor["actor_id"]
                                    actor_film["last_update"] = null
                                    actor_film
                                }
                                // group the film_actor stream by film_id
                                group { msg -> "${msg["film_id"]}" }
                            }
                        }
                        // ugly hack: As lists of messages can't be toplevel, a grouped message always consist of a single, otherwise empty message, that only
                        // contains one field, which is a list of the grouped messages, and that field is always named 'list'
                        // Ideas welcome
                        set { _, film, actorlist ->
                            film["actors"] = actorlist["list"] ?: emptyList<IMessage>()
                            film
                        }
                        // pass this message to the mongo sink
                        mongoSink("filmwithactors", "@filmwithcat", mongoConfig)
                        elasticSearchSink("elasticSink", "filmwithactors", "@filmwithactors", elasticConfig)

                    }
            )
        }.renderAndTest {
            Quarkus.waitForExit()
        }
        return 0
    }


}

