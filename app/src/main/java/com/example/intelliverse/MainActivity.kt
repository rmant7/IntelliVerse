package com.example.intelliverse

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.intelliverse.ui.theme.IntelliverseTheme
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest

class MainActivity : ComponentActivity() {

    private lateinit var splitInstallManager: SplitInstallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        splitInstallManager = SplitInstallManagerFactory.create(this)

        enableEdgeToEdge()
        setContent {
            IntelliverseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding)) {
                        loadDynamicModule()
                    }
                }
            }
        }
    }

    private fun loadDynamicModule() {
        val moduleName = "SchoolKiller" // Replace with the actual module name

        if (splitInstallManager.installedModules.contains(moduleName)) {
            Toast.makeText(this, "Module already installed", Toast.LENGTH_SHORT).show()
            navigateToDynamicFeature()
        } else {
            val request = SplitInstallRequest.newBuilder()
                .addModule(moduleName)
                .build()

            splitInstallManager.startInstall(request)
                .addOnSuccessListener {
                    Toast.makeText(this, "Module installed successfully", Toast.LENGTH_SHORT).show()
                    navigateToDynamicFeature()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to install module: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun navigateToDynamicFeature() {
        // Start the activity from the dynamic feature module
        try {
            val intent = Intent(this, Class.forName("com.schoolkiller.presentation.MainActivity"))
            startActivity(intent)
        } catch (e: ClassNotFoundException) {
            Toast.makeText(this, "Dynamic module activity not found", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, onButtonClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Greeting(name = "Android")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onButtonClick) {
            Text("Load Dynamic Feature")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    IntelliverseTheme {
        MainScreen(onButtonClick = {})
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    IntelliverseTheme {
        Greeting("Android")
    }
}