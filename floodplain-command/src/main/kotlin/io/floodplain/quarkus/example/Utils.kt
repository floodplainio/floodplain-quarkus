package io.floodplain.quarkus.example


private val logger = mu.KotlinLogging.logger {}

fun waitUntilCtrlC() {
    val thread = Thread.currentThread()
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            println("CTRLC detected!")
            thread.interrupt()
        }
    })
    var interrupted = false
    while(!interrupted) {
        logger.info("Streaming. Press CTRL+C to stop streaming changes")
        try {
            Thread.sleep(30000)
        } catch (e: InterruptedException) {
            // ignore
            interrupted = true
        }
    }
}