package com.example.passwordmanager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.passwordmanager.ui.theme.poppinsFontFamily


@Composable
fun TextFieldAndLabel(
    value: String,
    placeholderText: String = "",
    labelText: String = "",
    leadingIcon: ImageVector = Icons.Outlined.Mail,
    onChange: (String) -> Unit = { println(it) },
){
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            fontFamily = poppinsFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
            text = labelText)
        OutlinedTextField(
            value = value,
            shape = RoundedCornerShape(corner = CornerSize(24.dp)),
            leadingIcon ={
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = ""
                )
            },
            modifier = Modifier.background(color = Color(0xFFEEF0F2)),
            onValueChange = {
                onChange(it)
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            placeholder = {
                Text(text = placeholderText)
            }
        )

    }
}

@Composable
fun SearchField(
    value: String ,
    onChange: (String)->Unit = {println(it)}
){
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            shape = RoundedCornerShape(corner = CornerSize(24.dp)),
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = "")
            },
            modifier = Modifier.background(color = Color(0xFFEEF0F2)),
            onValueChange = {
                onChange(it)
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            placeholder = {
                Text(text = "Search Password")
            }
        )

    }
}


@Preview(showBackground = true)
@Composable
fun shows(){
    Column(modifier = Modifier.padding(8.dp)) {
        val value = ""
        SearchField(value = value)
        TextFieldAndLabel(value = value, labelText = "Username", placeholderText = "Username")
    }
}