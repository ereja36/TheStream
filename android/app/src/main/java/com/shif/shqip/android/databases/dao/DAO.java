package com.shif.shqip.android.databases.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChannel(ChannelEntity channel);

    @Query("DELETE FROM channel WHERE channel_id = :channel_id")
    void deleteChannel(String channel_id);

    @Query("DELETE FROM channel")
    void deleteAllChannel();

    @Query("SELECT * FROM channel ORDER BY saved_date DESC")
    List<ChannelEntity> getAllChannel();

    @Query("SELECT COUNT(channel_id) FROM channel")
    Integer getChannelCount();

    @Query("SELECT * FROM channel WHERE channel_id = :channel_id LIMIT 1")
    ChannelEntity getChannel(String channel_id);

}
