package app.undertow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.undertow.designsystem.UndertowTheme
import app.undertow.navigation.UndertowNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. It owns nothing but the theme and the navigation shell;
 * each screen is a separate feature module.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UndertowTheme {
                UndertowNavHost()
            }
        }
    }
}
