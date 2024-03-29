package com.thirdgate.stormtracker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Hide the status bar
        actionBar?.hide()


        setContent {
            MyApp()

        }
    }
}

@Composable
fun MyApp() {
    val apiService = remember { ApiService() }
    var stormsData by remember { mutableStateOf(emptyList<StormImageData>()) }

    LaunchedEffect(apiService) {
        fetchStorms(apiService) { newData ->
            stormsData = stormsData + newData
        }
    }

    MaterialTheme {
        Surface {
            MainScreen(stormsData)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(stormsData: List<StormImageData>) {

    var selectedTab by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val articleType = when (selectedTab) {
        0 -> "Plots"
        else -> "A"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Storm Tracker: " + articleType.replaceFirstChar { it.uppercase() })
                },
                Modifier.background(color = MaterialTheme.colorScheme.primary),
                //contentColor = MaterialTheme.colors.onPrimary,
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            modifier = Modifier.background(color = MaterialTheme.colorScheme.background),
                            text = { Text(text = "About App") },
                            onClick = {      // Create and launch the Intent here
                                val intent = Intent(context, AboutActivity::class.java)
                                context.startActivity(intent)
                                showMenu = false  // close the menu after launching the activity}
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Plots") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
//                NavigationBarItem(
//                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
//                    label = { Text("NotSet") },
//                    selected = selectedTab == 1,
//                    onClick = { selectedTab = 1 }
//                )
//                NavigationBarItem(
//                    icon = { Icon(Icons.Default.List, contentDescription = null) },
//                    label = { Text("NotSet") },
//                    selected = selectedTab == 2,
//                    onClick = { selectedTab = 2 }
//                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    DisplayStormPlots(stormsData)
                }

//                1 -> {
//                    Text("B")
//                }
//
//                2 -> {
//                    Text("C")
//                }
            }
        }
    }
}

suspend fun fetchStorms(apiService: ApiService, onNewDataFetched: (StormImageData) -> Unit) {
    val fetchedStorms = apiService.getStorms()

    val storms = fetchedStorms.storms

    storms.forEach { storm ->
        // Launch a new coroutine for each storm
        val id = storm.id
        val date = storm.date
        var imageBitmap: ImageBitmap? = null
        var myImageBitmap: ImageBitmap? = null
        var compareImageBitmap: ImageBitmap? = null
        var spaghettiImageBitmap: ImageBitmap? = null

        if (apiService.hasStormImage(date, id)) {
            val imageBytes = apiService.getStormImage(date, id)
            Log.i("ApiService", "fetchStorms fetched tropycal image from url")
            val bitmap = byteArrayToBitmap(imageBytes)
            imageBitmap = bitmap.asImageBitmap()
        }

        try {
            val myImageBytes = apiService.getStormMyImage(date, id)
            Log.i("ApiService", "fetchStorms fetched myImage from url")
            val myBitmap = byteArrayToBitmap(myImageBytes)
            myImageBitmap = myBitmap.asImageBitmap()
        } catch (e: Exception) {
            // Handle error or just log it
        }

        try {
            val myImageBytes = apiService.getStormCompareImage(date, id)
            Log.i("ApiService", "Fetched StormCompare image from url")
            val myBitmap = byteArrayToBitmap(myImageBytes)
            compareImageBitmap = myBitmap.asImageBitmap()
        } catch (e: Exception) {
            // Handle error or just log it
        }

        try {
            val myImageBytes = apiService.getStormSpaghettiImage(date, id)
            Log.i("ApiService", "Fetched StormCompare image from url")
            val myBitmap = byteArrayToBitmap(myImageBytes)
            spaghettiImageBitmap = myBitmap.asImageBitmap()
        } catch (e: Exception) {
            // Handle error or just log it
        }

        onNewDataFetched(
            StormImageData(
                storm,
                imageBitmap,
                myImageBitmap,
                compareImageBitmap,
                spaghettiImageBitmap
            )
        )
    }
}

@Composable
fun DisplayStormPlots(stormsData: List<StormImageData>) {

    LazyColumn {
        items(stormsData.size) { index ->
            //val (storm, imageBitmap, myImageBitmap, compareImageBitmap, spaghettiImageBitmap) = stormsData[index]

            val stormDataItem = stormsData[index]
            val storm = stormDataItem.basicStormInfo
            val imageBitmap = stormDataItem.imageBitmap
            val myImageBitmap = stormDataItem.myImageBitmap
            val compareImageBitmap = stormDataItem.compareImageBitmap
            val spaghettiImageBitmap = stormDataItem.spaghettiImageBitmap


            val id = storm.id
            val date = storm.date
            val titleSize = 22.sp
            val descriptionSize = 14.sp

            Text(
                "Storm ID: $id",
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                style = TextStyle(fontSize = titleSize)
            )
            Text(
                "Forecast date: $date",
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                style = TextStyle(fontSize = descriptionSize)
            )

            Text(
                "description: troPYcal",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontSize = descriptionSize)
            )
            imageBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Image for $id"
                )
            }
            //Spacer(modifier = Modifier.padding(10.dp))


            Text(
                "description: Orthographic Projection",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontSize = descriptionSize)
            )
            myImageBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "My Image for $id",

                    )
            }


            Text(
                "description: Model comparison, each line represents a different weather model",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontSize = descriptionSize)
            )

            compareImageBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Comparison Image for $id"
                )
            }

            Text(
                "historical comparison of model. Each line is a different forecast, usually once every 6 hours",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontSize = descriptionSize)
            )

            spaghettiImageBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Spaghetti Image for $id"
                )
            }

            Spacer(modifier = Modifier.padding(10.dp))
            Divider()
        }

    }
}


fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}

