package com.santiagojorda.mediasync.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.santiagojorda.mediasync.data.local.entity.ConnectedAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedAccountDao {

    @Query("SELECT * FROM connected_accounts ORDER BY email")
    fun observeAccounts(): Flow<List<ConnectedAccountEntity>>

    @Query("SELECT * FROM connected_accounts WHERE email = :email")
    suspend fun getByEmail(email: String): ConnectedAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: ConnectedAccountEntity)

    @Delete
    suspend fun delete(account: ConnectedAccountEntity)
}
