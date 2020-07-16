package io.floodplain.quarkus.example.io.floodplain.quarkus.example
import io.floodplain.elasticsearch.elasticSearchConfig
import io.floodplain.elasticsearch.elasticSearchSink
import io.floodplain.kotlindsl.*
import io.floodplain.mongodb.mongoConfig
import io.floodplain.mongodb.mongoSink
import io.floodplain.quarkus.example.waitUntilCtrlC
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain

private val logger = mu.KotlinLogging.logger {}

class AddressToElastic: QuarkusApplication {
    override fun run(vararg args: String?): Int {
        streams {
            val postgresConfig = postgresSourceConfig("mypostgres", "localhost", 5432, "postgres", "mysecretpassword", "dvdrental", "public")
            val elasticConfig = elasticSearchConfig("elastic", "http://localhost:9200")

            listOf(
                    postgresSource("address",postgresConfig) {
                        joinRemote({ msg -> "${msg["city_id"]}" }, false) {
                            postgresSource("city",postgresConfig) {
                                joinRemote({ msg -> "${msg["country_id"]}" }, false) {
                                    postgresSource("country",postgresConfig) {}
                                }
                                set { _, msg, state ->
                                    msg.set("country", state)
                                }
                            }
                        }
                        set { _, msg, state ->
                            msg.set("city", state)
                        }

                        sink("@address")
                        // elasticSearchSink("@address", "@address", "@address", elasticConfig)
                    },
                    postgresSource("customer",postgresConfig) {
                        joinRemote({ m -> "${m["address_id"]}" }, false) {
                            source("@address") {}
                        }
                        set { _, msg, state ->
                            msg.set("address", state)
                        }
                        elasticSearchSink("@customer", "@customer", "@customer", elasticConfig)
                    },
                    postgresSource("store",postgresConfig) {
                        joinRemote({ m -> "${m["address_id"]}" }, false) {
                            source("@address") {}
                        }
                        set { _, msg, state ->
                            msg.set("address", state)
                        }
                        elasticSearchSink("@store", "@store", "@store", elasticConfig)
                    },
                    postgresSource("staff",postgresConfig) {
                        joinRemote({ m -> "${m["address_id"]}" }, false) {
                            source("@address") {}
                        }
                        set { _, msg, state ->
                            msg.set("address", state)
                        }
                        elasticSearchSink("@staff", "@staff", "@staff", elasticConfig)
                    })
        }.renderAndTest {
            waitUntilCtrlC()
        }
        println("done!")
        return 0
    }


}

