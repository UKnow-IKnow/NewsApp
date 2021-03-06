package com.example.newsapp.UI

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.*
import android.net.NetworkCapabilities.*
import android.os.Build
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newsapp.UI.database.ArticleDatabase
import com.example.newsapp.UI.models.Article
import com.example.newsapp.UI.models.NewsResponce
import com.example.newsapp.UI.repository.NewsRepository
import com.example.newsapp.UI.util.Resource
import kotlinx.coroutines.launch
import okio.IOException
import retrofit2.Response

class NewsViewModel(
    application: Application
) : AndroidViewModel(application) {


    private val newsRepository = NewsRepository(ArticleDatabase(context = application))

    val breakingNews: MutableLiveData<Resource<NewsResponce>> = MutableLiveData()
    var breakingNewsPage = 1
    var breakingNewsResponce: NewsResponce? = null

    val searchNews: MutableLiveData<Resource<NewsResponce>> = MutableLiveData()
    var searchNewsPage = 1
    var searhNewsResponce: NewsResponce? = null

    init {
        getBreakingNews("IN")
    }

    fun getBreakingNews(countrycode: String) = viewModelScope.launch {
      safeBreakingNewsCall(countrycode)

    }

    fun searchNews(searchQuery: String) = viewModelScope.launch {
        safeSearchNewsCall(searchQuery)
    }

    private fun handleBreakingNewsResponce(responce: Response<NewsResponce>) : Resource<NewsResponce>{
        if(responce.isSuccessful){
            responce.body()?.let { resultResponce ->
                breakingNewsPage++
                if (breakingNewsResponce == null){
                    breakingNewsResponce = resultResponce
                }else{
                    val oldArticle = breakingNewsResponce?.articles
                    val newArticle = resultResponce.articles
                    oldArticle?.addAll(newArticle)
                }
                return Resource.Success(breakingNewsResponce ?:resultResponce)
            }
        }
        return Resource.Error(responce.message())
    }

    private fun handleSearchNewsResponce(responce: Response<NewsResponce>) : Resource<NewsResponce>{
        if(responce.isSuccessful){
            responce.body()?.let { resultResponce ->
                searchNewsPage++
                if (searhNewsResponce == null){
                    searhNewsResponce = resultResponce
                }else{
                    val oldArticle = searhNewsResponce?.articles
                    val newArticle = resultResponce.articles
                    oldArticle?.addAll(newArticle)
                }
                return Resource.Success(searhNewsResponce ?:resultResponce)
            }
        }
        return Resource.Error(responce.message())
    }

    fun savedArticle(article: Article) = viewModelScope.launch {
        newsRepository.upsert(article)
    }

    fun getSavedNews() = newsRepository.getSavedNews()

    fun deleteArticle(article: Article) = viewModelScope.launch {
        newsRepository.deleteArticle(article)
    }

    private suspend fun safeSearchNewsCall(searchQuery: String) {
        searchNews.postValue(Resource.Loading())
        try {
            if (hasInternetConnection()) {
                val responce = newsRepository.searchForNews(searchQuery,searchNewsPage)
                searchNews.postValue(handleSearchNewsResponce(responce))
            }else {
                searchNews.postValue(Resource.Error("No internet connection"))
            }
        }catch (t: Throwable){
            when(t) {
                is IOException -> searchNews.postValue(Resource.Error("Network failure"))
                else -> searchNews.postValue(Resource.Error("Conversion Error"))
            }
        }
    }

    private suspend fun safeBreakingNewsCall(countrycode: String) {
        breakingNews.postValue(Resource.Loading())
        try {
            if (hasInternetConnection()) {
                val responce = newsRepository.getBreakingNews(countrycode,breakingNewsPage)
                breakingNews.postValue(handleBreakingNewsResponce(responce))
            }else {
                breakingNews.postValue(Resource.Error("No internet connection"))
            }
        }catch (t: Throwable){
            when(t) {
                is IOException -> breakingNews.postValue(Resource.Error("Network failure"))
                else -> breakingNews.postValue(Resource.Error("Conversion Error"))
            }
        }
    }

    private fun hasInternetConnection() : Boolean{
        val connectivityManager = getApplication<NewsApplication>().getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return when {
                capabilities.hasTransport(TRANSPORT_WIFI) -> true
                capabilities.hasTransport(TRANSPORT_CELLULAR) -> true
                capabilities.hasTransport(TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }else {
            connectivityManager.activeNetworkInfo ?.run {
                return when(type) {
                    TYPE_WIFI -> true
                    TYPE_MOBILE -> true
                    TYPE_ETHERNET -> true
                    else -> false
                }
            }
        }
        return false
    }

}