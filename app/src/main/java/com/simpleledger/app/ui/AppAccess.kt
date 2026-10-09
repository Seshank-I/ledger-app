package com.simpleledger.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.simpleledger.app.AppContainer
import com.simpleledger.app.LedgerApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as LedgerApp).container
