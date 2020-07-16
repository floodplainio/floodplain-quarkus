package io.floodplain.quarkus.example.io.floodplain.quarkus.example
import io.floodplain.kotlindsl.*
import io.floodplain.mongodb.mongoConfig
import io.floodplain.mongodb.mongoSink
import io.floodplain.quarkus.example.waitUntilCtrlC
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain

private val logger = mu.KotlinLogging.logger {}

@QuarkusMain(name="address2mongo")
class AddressToMongo: QuarkusApplication {
    override fun run(vararg args: String?): Int {
        streams {
            val postgresConfig = postgresSourceConfig(
                "mypostgres",
                "localhost",
                5432,
                "postgres",
                "mysecretpassword",
                "dvdrental",
                "public"
            )
            val mongoConfig = mongoConfig(
                "mongosink",
                "mongodb://localhost:27017",
                "@mongodump"
            )
            listOf(
                postgresSource("address", postgresConfig) {
                    joinRemote({ msg -> "${msg["city_id"]}" }, false) {
                        postgresSource("city", postgresConfig) {
                            joinRemote({ msg -> "${msg["country_id"]}" }, false) {
                                postgresSource("country", postgresConfig) {}
                            }
                            set { _, msg, state ->
                                msg.set("country", state)
                            }
                        }
                    }
                    set { _, msg, state ->
                        msg.set("city", state)
                    }
                    sink("@address", false)
                },
                postgresSource("customer", postgresConfig) {
                    joinRemote({ m -> "${m["address_id"]}" }, false) {
                        source("@address") {}
                    }
                    set { _, msg, state ->
                        msg.set("address", state)
                    }
                    mongoSink("customer", "@customer", mongoConfig)
                },
                postgresSource("store", postgresConfig) {
                    joinRemote({ m -> "${m["address_id"]}" }, false) {
                        source("@address") {}
                    }
                    set { _, msg, state ->
                        msg.set("address", state)
                    }
                    mongoSink("store", "@store", mongoConfig)
                },
                postgresSource("staff", postgresConfig) {
                    joinRemote({ m -> "${m["address_id"]}" }, false) {
                        source("@address") {}
                    }
                    set { _, msg, state ->
                        msg.set("address", state)
                        msg["address_id"] = null
                        msg
                    }
                    mongoSink("staff", "@staff", mongoConfig)
                })
        }.renderAndTest {
            Quarkus.waitForExit()
        }
        return 0
    }


}

