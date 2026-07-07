package com.santiagojorda.baul.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedAccountDao {

    @Query("SELECT * FROM connected_accounts ORDER BY email")
    fun observeAccounts(): Flow<List<ConnectedAccountEntity>>

    @Query("SELECT * FROM connected_accounts WHERE email = :email")
    suspend fun getByEmail(email: String): ConnectedAccountEntity?

    @Query("SELECT * FROM connected_accounts ORDER BY connectedAt ASC LIMIT 1")
    suspend fun getFirstConnected(): ConnectedAccountEntity?

    @Query("SELECT * FROM connected_accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ConnectedAccountEntity?

    @Query("SELECT COUNT(*) FROM connected_accounts")
    suspend fun count(): Int

    @Query("UPDATE connected_accounts SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE connected_accounts SET isDefault = 1 WHERE email = :email")
    suspend fun markAsDefault(email: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: ConnectedAccountEntity)

    @Delete
    suspend fun delete(account: ConnectedAccountEntity)
}
