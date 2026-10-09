package com.simpleledger.app.ui

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

/** Ignores a second quick tap on Back so it can never pop the home screen. */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
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
            )
        }
        composable("addPerson") {
            AddPersonScreen(
                onBack = nav::back,
                onSaved = { id -> nav.navigate("person/$id") { popUpTo("addPerson") { inclusive = true } } },
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
            )
        }
        composable("add/{personId}/{direction}") { entry ->
            AddEntryScreen(
                personId = entry.arguments?.getString("personId").orEmpty(),
                direction = Direction.valueOf(entry.arguments?.getString("direction") ?: Direction.GAVE.name),
                onBack = nav::back,
                onSaved = nav::back,
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
        composable("settings") {
            SettingsScreen(onBack = nav::back, onPin = { mode -> nav.navigate("pin/$mode") })
        }
        composable("pin/{mode}") { entry ->
            PinScreen(mode = entry.arguments?.getString("mode") ?: "set", onDone = nav::back, onBack = nav::back)
        }
    }
}
