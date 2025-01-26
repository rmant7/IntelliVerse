package com.calories.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calories.R

@Composable
fun StaticLabelTextField(
    modifier: Modifier = Modifier,
    value:String,
    onValueChange : (String)->Unit,
    placeholderText: String = "",
    singleLine:Boolean = false
){

    val textColor = if (value.isEmpty())
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.primary

    val placeHolder = remember {
        mutableStateOf(placeholderText)
    }

    OutlinedTextField(
        modifier = modifier
            .onFocusChanged {
                placeHolder.value =
                    if (it.isFocused) ""
                    else placeholderText
            }
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        value = value,
        onValueChange = {
            onValueChange(it)
        },
        label = {
            Text(
                text = stringResource(
                    id = R.string.additional_information_TextField_label
                )
            )
        },
        visualTransformation = if (value.isEmpty())
            PlaceholderTransformation(placeholder = placeHolder.value)
        else VisualTransformation.None,
        textStyle = TextStyle(color = textColor, fontSize = 18.sp),
        singleLine = singleLine
    )
}

private class PlaceholderTransformation(private val placeholder: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return placeholderFilter(placeholder)
    }
}

fun placeholderFilter(placeholder: String): TransformedText {

    val numberOffsetTranslator = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            return 0
        }

        override fun transformedToOriginal(offset: Int): Int {
            return 0
        }
    }

    return TransformedText(AnnotatedString(placeholder), numberOffsetTranslator)
}