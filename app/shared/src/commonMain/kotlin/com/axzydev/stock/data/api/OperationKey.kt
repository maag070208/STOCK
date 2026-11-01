package com.axzydev.stock.data.api

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun newOperationKey(): String = Uuid.random().toString()
