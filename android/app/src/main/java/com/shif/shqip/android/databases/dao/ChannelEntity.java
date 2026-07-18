package com.shif.shqip.android.databases.dao;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.shif.shqip.android.models.Channel;
import com.google.gson.annotations.Expose;

@Entity(tableName = "channel")
public class ChannelEntity {

    @PrimaryKey
    @NonNull
    public String channel_id = "";

    @Expose
    @ColumnInfo(name = "channel_name")
    public String channel_name = "";

    @Expose
    @ColumnInfo(name = "channel_image")
    public String channel_image = "";

    @Expose
    @ColumnInfo(name = "channel_url")
    public String channel_url = "";

    @Expose
    @ColumnInfo(name = "channel_description")
    public String channel_description = "";

    @Expose
    @ColumnInfo(name = "channel_type")
    public String channel_type = "";

    @Expose
    @ColumnInfo(name = "video_id")
    public String video_id = "";

    @Expose
    @ColumnInfo(name = "category_name")
    public String category_name = "";

    @Expose
    @ColumnInfo(name = "user_agent")
    public String user_agent = "";

    @Expose
    @ColumnInfo(name = "stream_referer")
    public String stream_referer = "";

    @Expose
    @ColumnInfo(name = "stream_origin")
    public String stream_origin = "";

    @Expose
    @ColumnInfo(name = "saved_date")
    public long saved_date = System.currentTimeMillis();

    public ChannelEntity() {
    }

    public static ChannelEntity entity(Channel radio) {
        ChannelEntity entity = new ChannelEntity();
        entity.channel_id = radio.channel_id;
        entity.channel_name = radio.channel_name;
        entity.channel_image = radio.channel_image;
        entity.channel_url = radio.channel_url;
        entity.channel_description = radio.channel_description;
        entity.channel_type = radio.channel_type;
        entity.video_id = radio.video_id;
        entity.category_name = radio.category_name;
        entity.user_agent = radio.user_agent;
        entity.stream_referer = radio.stream_referer;
        entity.stream_origin = radio.stream_origin;
        return entity;
    }

    public Channel original() {
        Channel channel = new Channel();
        channel.channel_id = channel_id;
        channel.channel_name = channel_name;
        channel.channel_image = channel_image;
        channel.channel_url = channel_url;
        channel.channel_description = channel_description;
        channel.channel_type = channel_type;
        channel.video_id = video_id;
        channel.category_name = category_name;
        channel.user_agent = user_agent;
        channel.stream_referer = stream_referer;
        channel.stream_origin = stream_origin;
        return channel;
    }
}