package com.ifpr.androidapptemplate.baseclasses

data class Item(
    var endereco: String? = null,
    val base64Image: String? = null,
    val imageUrl: String? = null,
    val categoria: String? = null,
    val duracao: Int? = null,
    val dataEstudo: String? = null
)