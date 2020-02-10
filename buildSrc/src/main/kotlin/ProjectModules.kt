import dev.whyoleg.kamp.project.*

//Autogenerated with kamp, don't change

object ProjectModules {
    val cli = ProjectModule(":cli")
    val benchmarks = ProjectModule(":benchmarks")
    val tdlib = ProjectModule(":tdlib")
    val json = ProjectModule(":json")
    val core = ProjectModule(":core")
    val client = ProjectModule(":client")
    val api = ProjectModule(":api")
    val core_api = ProjectModule(":core-api")
    val bots_api = ProjectModule(":bots-api")
    val test_api = ProjectModule(":test-api")
    val deprecated_api = ProjectModule(":deprecated-api")
    val clients = ProjectModule(":clients")

    object Clients {
        val client_deferred = ProjectModule(":clients-client-deferred")
        val client_coroutines = ProjectModule(":clients-client-coroutines")
    }
    val updates = ProjectModule(":updates")

    object Updates {
        val updates_flow = ProjectModule(":updates-updates-flow")
    }
}
