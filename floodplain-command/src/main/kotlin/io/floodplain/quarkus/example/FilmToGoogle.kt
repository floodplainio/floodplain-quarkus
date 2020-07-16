package io.floodplain.quarkus.example

import io.floodplain.kotlindsl.postgresSource
import io.floodplain.kotlindsl.postgresSourceConfig
import io.floodplain.kotlindsl.set
import io.floodplain.kotlindsl.stream
import io.floodplain.sink.sheet.googleSheetConfig
import io.floodplain.sink.sheet.googleSheetsSink
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain

private val logger = mu.KotlinLogging.logger {}

class FilmToGoogle: QuarkusApplication {

    override fun run(vararg args: String?): Int {
        stream() {
            var spreadsheetId = "1COkG3-Y0phnHKvwNiFpYewKhT3weEC5CmzmKkXUpPA4"
            val postgresConfig = postgresSourceConfig(
                    "mypostgres",
                    "localhost",
                    5432,
                    "postgres",
                    "mysecretpassword",
                    "dvdrental",
                    "public"
            )
            val sheetConfig = googleSheetConfig("sheets")
            postgresSource("film",postgresConfig) {
                // Clear the last_update field, it makes no sense in a denormalized situation
                set { _, film, _ ->
                    film["last_update"] = null
                    film["_row"] = film.integer("film_id").toLong()
                    logger.info("Flm: $film")
                    film["special_features"] = film.list("special_features").joinToString(",")
                    film
                }
                googleSheetsSink("outputtopic", spreadsheetId, listOf("title", "rating", "release_year", "rental_duration", "special_features", "description"), "D",4,sheetConfig)
            }
        }.renderAndTest {
            Quarkus.waitForExit()
        }
        return 0
    }

}