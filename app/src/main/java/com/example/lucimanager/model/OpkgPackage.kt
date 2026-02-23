package com.example.lucimanager.model

data class OpkgPackage(
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val isInstalled: Boolean = false
)
