package com.shif.shqip.android.rests;

import com.shif.shqip.android.callbacks.CallbackCategories;
import com.shif.shqip.android.callbacks.CallbackChannel;
import com.shif.shqip.android.callbacks.CallbackChannelDetail;
import com.shif.shqip.android.callbacks.CallbackConfig;
import com.shif.shqip.android.callbacks.CallbackDetailCategory;
import com.shif.shqip.android.callbacks.CallbackUser;
import com.shif.shqip.android.models.Resp;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiInterface {

    String CACHE = "Cache-Control: max-age=0";
    String AGENT = "Data-Agent: The Stream";

    @Headers({CACHE, AGENT})
    @GET("api.php?get_posts")
    Call<CallbackChannel> getRecentChannel(
            @Query("page") int page,
            @Query("count") int count,
            @Query("api_key") String api_key
    );

    @Headers({CACHE, AGENT})
    @GET("api.php?get_category_posts")
    Call<CallbackDetailCategory> getChannelByCategory(
            @Query("id") int id,
            @Query("page") int page,
            @Query("count") int count,
            @Query("api_key") String api_key
    );

    @Headers({CACHE, AGENT})
    @GET("api.php?get_category_index")
    Call<CallbackCategories> getAllCategories(
            @Query("api_key") String api_key
    );

    @Headers({CACHE, AGENT})
    @GET("api.php?get_search_results")
    Call<CallbackChannel> getSearchChannel(
            @Query("search") String search,
            @Query("count") int count,
            @Query("api_key") String api_key
    );

    @Headers({CACHE, AGENT})
    @GET("api.php?get_search_results_rtl")
    Call<CallbackChannel> getSearchChannelRTL(
            @Query("search") String search,
            @Query("count") int count,
            @Query("api_key") String api_key
    );

    @Headers({CACHE, AGENT})
    @GET("api.php?get_post_detail")
    Call<CallbackChannelDetail> getChannelDetail(
            @Query("id") String id
    );

    @Headers({CACHE, AGENT})
    @GET("api.php?get_config")
    Call<CallbackConfig> getConfig(
            @Query("package_name") String package_name,
            @Query("api_key") String api_key
    );

    @FormUrlEncoded
    @POST("api.php?update_view")
    Call<Resp> updateView(
            @Field("id") String id
    );

    @Headers({CACHE, AGENT})
    @GET("user.json")
    Call<CallbackUser> getUsers();

}
