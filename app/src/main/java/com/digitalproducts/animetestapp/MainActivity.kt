package com.digitalproducts.animetestapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Decided to put everything in a single file only to make the review of this code easier
// MVVM arch (Model, View, ViewModel)
// Coroutines for making asynchronous
// LiveData for observing our data from the ViewModel - Reactive pattern
// Using Glide for Image loading

class MainActivity : AppCompatActivity() {

    lateinit var searchBar: SearchView
    lateinit var recyclerView: RecyclerView
    lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Because the lack of time, did not use viewbinding
        searchBar = findViewById(R.id.search_bar)
        recyclerView = findViewById(R.id.recyclerview)
        progressBar = findViewById(R.id.progressBar)
        //Recycler Necessities
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recyclerView.layoutManager = layoutManager

        val vm = MainViewModel()


        searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                vm.onQuerySubmitted(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                //Not really interested in this atm
                return false
            }
        })
        // viewModel observables / observers
        vm.run {
            listOfShows.observe(this@MainActivity) {
                val adapter = MyAdapter(this@MainActivity, it) { position ->
                    vm.itemWasClicked(position)
                }
                recyclerView.adapter = adapter
            }
            showLoadingIndicator.observe(this@MainActivity){ showLoading ->
                progressBar.visibility = if (showLoading) View.VISIBLE else View.GONE
            }
        }
    }
}

// Normally would extract this to its own file
class MainViewModel : ViewModel() {

    // Livedata so that the Activity can observe - ReActive Programming
    val listOfShows = MutableLiveData<List<ShowInfo>>()
    val showLoadingIndicator = MutableLiveData(true)

    init {
        startApiRequest("naruto")
    }

    private fun startApiRequest(anime: String) {
        // Went with Coroutines over RX for its ease of use
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiResponse = ApiClient.apiService.getCharacterInfo(anime)
                showLoadingIndicator.postValue(false)
                listOfShows.postValue(apiResponse.listOfShows)
            } catch (e: Exception) {
                Log.e("miko", "Error is $e")
            }
        }
    }

    fun itemWasClicked(position: Int) {
        // to handle click events from the Recyclerview
    }

    fun onQuerySubmitted(anime: String?) {
        if (!anime.isNullOrBlank()){
            showLoadingIndicator.value = true
            startApiRequest(anime)
        }
    }
}

// Simple Retrofit client
object ApiClient {
    private const val BASE_URL = "https://api.jikan.moe/v3/search/"

    private val gson: Gson by lazy {
        GsonBuilder().setLenient().create()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

// Our Api service will hold all our different calls coming from this Base URL
interface ApiService {
    @GET("anime")
    suspend fun getCharacterInfo(
        @Query(
            value = "q",
            encoded = true
        ) anime: String
    ): ApiResponseObject
}

//data classes for response object
data class ApiResponseObject(
    @SerializedName("results")
    val listOfShows: List<ShowInfo>
)

// Data Model representing the list of Data
data class ShowInfo(
    @SerializedName("mal_id")
    val id: Int,
    @SerializedName("url")
    val url: String,
    @SerializedName("image_url")
    val imageUrl: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("airing")
    val isAiring: Boolean,
    @SerializedName("synopsis")
    val synopsis: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("episodes")
    val episodes: Int,
    @SerializedName("score")
    val score: Float,
    @SerializedName("start_date")
    val startDate: String,
    @SerializedName("end_date")
    val endDate: String,
    @SerializedName("members")
    val members: Int,
    @SerializedName("rated")
    val rated: String
)

// Adapter for Recyclerview to display list of shows
class MyAdapter(
    val context: Context,
    val list: List<ShowInfo>,
    val listener: (position: Int) -> Unit
) :
    RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_show_details, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.title.text = list[position].title
        holder.rating.text = list[position].rated
        holder.episodes.text = list[position].episodes.toString()
        holder.score.text = list[position].score.toString()
        holder.description.text = list[position].synopsis
        Glide.with(context).load(list[position].imageUrl).into(holder.imageView)
    }

    override fun getItemCount() = list.size

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.list_item_image_view)
        val title: TextView = itemView.findViewById(R.id.list_item_title)
        val rating: TextView = itemView.findViewById(R.id.list_item_rating)
        val episodes: TextView = itemView.findViewById(R.id.list_item_number_of_episodes)
        val score: TextView = itemView.findViewById(R.id.list_item_score)
        val description: TextView = itemView.findViewById(R.id.list_item_description)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) listener.invoke(adapterPosition)
            }
        }
    }
}