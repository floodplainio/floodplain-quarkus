package io.floodplain

import io.floodplain.kotlindsl.group
import io.floodplain.kotlindsl.joinGrouped
import io.floodplain.kotlindsl.joinRemote
import io.floodplain.kotlindsl.message.IMessage
import io.floodplain.kotlindsl.message.empty
import io.floodplain.kotlindsl.mongoConfig
import io.floodplain.kotlindsl.mongoSink
import io.floodplain.kotlindsl.postgresSource
import io.floodplain.kotlindsl.postgresSourceConfig
import io.floodplain.kotlindsl.set
import io.floodplain.kotlindsl.stream
import java.net.URL
import javax.inject.Inject
import javax.sql.DataSource
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.annotation.PostConstruct
import javax.enterprise.inject.Default

@Path("/hello")
@Default
class ExampleResource {


    fun createRuntime() {
            stream {
                val postgresConfig = postgresSourceConfig("mypostgres", "postgres", 5432, "postgres", "mysecretpassword", "dvdrental")
                val mongoConfig = mongoConfig("mongosink", "mongodb://mongo", "@mongodump")
                // Start with the 'film' collection
                postgresSource("public", "film", postgresConfig) {
                    // Clear the last_update field, it makes no sense in a denormalized situation
                    set { _, film, _ ->
                        film["last_update"] = null; film
                    }
                    // Join with something that also uses the film_id key space.
                    // optional = true so films without any actors (animation?) will propagate
                    // multiple = true we're not joining with something that actually 'has' a film id
                    // we are joining with something that is grouped by film_id
                    joinGrouped(optional = true) {

                        postgresSource("public", "film_actor", postgresConfig) {
                            joinRemote({ msg -> "${msg["actor_id"]}" }, false) {
                                postgresSource("public", "actor", postgresConfig) {
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
                            // note the string wrapping: A key always needs to be a string
                            group { msg -> "${msg["film_id"]}" }
                        }
                    }
                    // ugly hack: As lists of messages can't be top level, a grouped message always consist of a single, otherwise empty message, that only
                    // contains one field, which is a list of the grouped messages, and that field is always named 'list'
                    // Ideas welcome
                    set { _, film, actorlist ->
                        film["actors"] = actorlist["list"] ?: emptyList<IMessage>()
                        film
                    }
                    // Now we're done with joining the actors. Let's join the categories, same drill, but simpler:
                    joinGrouped {
                        postgresSource("public", "film_category", postgresConfig) {
                            joinRemote({ msg -> "${msg["category_id"]}" }, true) {
                                postgresSource("public", "category", postgresConfig) {}
                            }
                            set { _, msg, category ->
                                msg["category"] = category["name"] ?: "unknown"
                                msg
                            }
                            group { msg -> "${msg["film_id"]}" }
                        }
                    }
                    set { _, msg, categories ->
                        msg["categories"] = categories["list"] ?: empty()
                        msg
                    }
                    joinRemote({ msg -> msg["language_id"].toString() }) {
                        postgresSource("public", "language", postgresConfig) {}
                    }
                    set { _, film, language ->
                        film["language"] = language["name"];
                        film["language_id"] = null
                        film
                    }
                    // pass this message to the mongo sink
                    mongoSink("filmwithactors", "filmwithcat", mongoConfig)
                }
            }.renderAndStart(URL("http://localhost:8083/connectors"), "localhost:9092")
        }
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun hello(): String {
        println("hello!")
        createRuntime()
        return "doei!"
    }
}