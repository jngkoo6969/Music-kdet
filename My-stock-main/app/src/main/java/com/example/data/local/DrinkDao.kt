package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DrinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkDao {

    @Query("SELECT * FROM drinks ORDER BY displayOrder ASC, id ASC")
    fun getAllDrinks(): Flow<List<DrinkEntity>>

    @Query("SELECT * FROM drinks WHERE isActive = 1 ORDER BY displayOrder ASC, id ASC")
    fun getActiveDrinks(): Flow<List<DrinkEntity>>

    @Query("SELECT * FROM drinks WHERE id = :id LIMIT 1")
    suspend fun getDrinkById(id: Long): DrinkEntity?

    @Query("SELECT * FROM drinks ORDER BY displayOrder ASC, id ASC")
    suspend fun getAllDrinksOnce(): List<DrinkEntity>

    @Query("SELECT COUNT(*) FROM drinks")
    suspend fun getDrinkCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrink(drink: DrinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrinks(drinks: List<DrinkEntity>)

    @Update
    suspend fun updateDrink(drink: DrinkEntity)

    @Delete
    suspend fun deleteDrink(drink: DrinkEntity)

    @Query("UPDATE drinks SET stock = :newStock, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStock(id: Long, newStock: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE drinks SET displayOrder = :order WHERE id = :id")
    suspend fun updateDisplayOrder(id: Long, order: Int)

    @Query("DELETE FROM drinks")
    suspend fun deleteAllDrinks()
}
