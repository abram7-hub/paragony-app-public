package pl.paragony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.paragony.ui.ParagonyApp
import pl.paragony.ui.theme.ParagonyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParagonyTheme {
                ParagonyApp()
            }
        }
    }
}
