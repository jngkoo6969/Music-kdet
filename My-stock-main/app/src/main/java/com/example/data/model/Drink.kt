package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drinks")
data class DrinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val price: Double, // in canonical currency USD
    val stock: Int,
    val lowStockThreshold: Int = 5,
    val isActive: Boolean = true,
    val displayOrder: Int = 0,
    val imageUri: String? = null,
    val colorHex: String = "#E0F2FE", // Soft pastel tint
    @ColumnInfo(defaultValue = "Other")
    val category: String = "Other",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
