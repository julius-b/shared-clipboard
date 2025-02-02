package app.mindspaces.clipboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleScaffold(
    modifier: Modifier,
    title: String,
    horizontal: Dp = 0.dp,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title.uppercase(),
                        fontSize = 24.sp,
                        fontFamily = FontFamily(Font(Res.font.Anton_Regular))
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(horizontal = horizontal).fillMaxSize()
            //.wrapContentSize()
        ) {
            content()
        }
    }
}
