package com.example.intelliverse.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.intelliverse.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(navController: NavController) {
    Scaffold(
        /*topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = com.example.shared.R.drawable.intelliverse), // Replace with your custom drawable
                        contentDescription = "App Logo",
                        modifier = Modifier.size(40.dp) // Adjust size as needed
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                )
            )
        }*/
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .wrapContentHeight()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.example.shared.R.drawable.intelliverse), // Reference the same drawable resource
                        contentDescription = "Intelliverse Icon",
                        modifier = Modifier
                            .size(32.dp) // Adjust size as needed
                            .padding(end = 8.dp), // Space between icon and text
                        tint = Color.Unspecified // Prevent tinting
                    )
                    Text(
                        "Welcome to Intelliverse",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }

                // SchoolKiller Button
                Button(
                    onClick = { navController.navigate("schoolKiller") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.schoolkiller), // Reference the drawable resource
                        contentDescription = "SchoolKiller Icon",
                        modifier = Modifier.size(24.dp), // Adjust size if needed
                        tint = Color.Unspecified // Prevent the icon from being tinted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to SchoolKiller")
                }

                // MatterOfChoice Button
                Button(
                    onClick = { navController.navigate("matterOfChoice") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = false
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.matterofchoice), // Reference the drawable resource
                        contentDescription = "MatterOfChoice Icon",
                        modifier = Modifier.size(24.dp), // Adjust size if needed
                        tint = Color.Unspecified // Prevent the icon from being tinted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to MatterOfChoice")
                }
            }
        }
    }
}