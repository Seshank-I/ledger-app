package com.simpleledger.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simpleledger.app.data.Direction
import com.simpleledger.app.ui.screens.AddEntryScreen
import com.simpleledger.app.ui.screens.AddInterestScreen
import com.simpleledger.app.ui.screens.AddPersonScreen
import com.simpleledger.app.ui.screens.EntryDetailScreen
import com.simpleledger.app.ui.screens.HomeScreen
import com.simpleledger.app.ui.screens.PersonScreen
import com.simpleledger.app.ui.screens.PinScreen
import com.simpleledger.app.ui.screens.SetInterestScreen
import com.simpleledger.app.ui.screens.SettingsScreen
import com.simpleledger.app.ui.screens.ShareStatementScreen
import com.simpleledger.app.ui.screens.VoiceScreen

/** Ignores a second quick tap on Back so it can never pop the home screen. */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

private fun NavController.openVoice(heard: String, personId: String?) {
    navigate("voice/${Uri.encode(heard)}?person=${personId.orEmpty()}") {
        // Saying something again replaces the previous voice screen instead of stacking up.
        popUpTo("voice/{heard}?person={person}") { inclusive = true }
    }
}

@Composable
fun LedgerNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onPerson = { nav.navigate("person/$it") },
                onAddPerson = { nav.navigate("addPerson") },
                onSettings = { nav.navigate("settings") },
                onHeard = { nav.openVoice(it, null) },
            )
        }
        composable("addPerson") {
            AddPersonScreen(
                onBack = nav::back,
                onSaved = { id -> nav.navigate("person/$id") { popUpTo("addPerson") { inclusive = true } } },
            )
        }
        composable("editPerson/{personId}") { entry ->
            AddPersonScreen(
                onBack = nav::back,
                onSaved = { nav.back() },
                editPersonId = entry.arguments?.getString("personId"),
            )
        }
        composable("person/{personId}") { entry ->
            val id = entry.arguments?.getString("personId").orEmpty()
            PersonScreen(
                personId = id,
                onBack = nav::back,
                onAdd = { dir -> nav.navigate("add/$id/${dir.name}") },
                onEntry = { seq -> nav.navigate("entry/$seq") },
                onSetInterest = { nav.navigate("setInterest/$id") },
                onAddInterest = { nav.navigate("addInterest/$id") },
                onShare = { nav.navigate("share/$id") },
                onHeard = { nav.openVoice(it, id) },
                onEdit = { nav.navigate("editPerson/$id") },
            )
        }
        composable(
            "add/{personId}/{direction}?amount={amount}&voice={voice}",
            arguments = listOf(
                navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
                navArgument("voice") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val amount = entry.arguments?.getLong("amount") ?: -1L
            AddEntryScreen(
                personId = entry.arguments?.getString("personId").orEmpty(),
                direction = Direction.valueOf(entry.arguments?.getString("direction") ?: Direction.GAVE.name),
                onBack = nav::back,
                onSaved = nav::back,
                prefillPaise = amount.takeIf { it > 0 },
                fromVoice = entry.arguments?.getBoolean("voice") ?: false,
            )
        }
        composable("entry/{seq}", arguments = listOf(navArgument("seq") { type = NavType.LongType })) { entry ->
            EntryDetailScreen(
                seq = entry.arguments?.getLong("seq") ?: 0L,
                onBack = nav::back,
                onEnterCorrect = { personId, dir ->
                    nav.navigate("add/$personId/${dir.name}") { popUpTo("entry/{seq}") { inclusive = true } }
                },
            )
        }
        composable("setInterest/{personId}") { entry ->
            SetInterestScreen(personId = entry.arguments?.getString("personId").orEmpty(), onBack = nav::back)
        }
        composable("addInterest/{personId}") { entry ->
            AddInterestScreen(personId = entry.arguments?.getString("personId").orEmpty(), onBack = nav::back)
        }
        composable("share/{personId}") { entry ->
            val id = entry.arguments?.getString("personId").orEmpty()
            ShareStatementScreen(personId = id, onBack = nav::back, onEditPerson = { nav.navigate("editPerson/$id") })
        }
        composable(
            "voice/{heard}?person={person}",
            arguments = listOf(navArgument("person") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            VoiceScreen(
                heard = entry.arguments?.getString("heard").orEmpty(),
                currentPersonId = entry.arguments?.getString("person")?.ifEmpty { null },
                onBack = nav::back,
                onHeardAgain = { nav.openVoice(it, entry.arguments?.getString("person")?.ifEmpty { null }) },
                onComplete = { personId, dir, amount ->
                    val amountArg = amount?.let { "&amount=$it" }.orEmpty()
                    nav.navigate("add/$personId/${dir.name}?voice=true$amountArg") {
                        popUpTo("voice/{heard}?person={person}") { inclusive = true }
                    }
                },
                onOpenPerson = { id -> nav.navigate("person/$id") { popUpTo("voice/{heard}?person={person}") { inclusive = true } } },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = nav::back, onPin = { mode -> nav.navigate("pin/$mode") })
        }
        composable("pin/{mode}") { entry ->
            PinScreen(mode = entry.arguments?.getString("mode") ?: "set", onDone = nav::back, onBack = nav::back)
        }
    }
}
