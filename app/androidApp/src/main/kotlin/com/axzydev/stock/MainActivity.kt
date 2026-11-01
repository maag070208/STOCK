package com.axzydev.stock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The canvas is light in light mode and dark in dark mode, so the status bar icons follow the mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.axzydev.stock.AndroidContext.app = applicationContext

        setContent {
            // The end-to-end tests find fields and buttons by their test tags, read as resource ids.
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                App()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}