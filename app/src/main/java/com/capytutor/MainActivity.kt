package com.capytutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.capytutor.ui.CapyNavHost
import com.capytutor.ui.theme.CapyScreenBackground
import com.capytutor.ui.theme.CapyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CapyTheme {
                CapyScreenBackground {
                    CapyNavHost()
                }
            }
        }
    }
}
