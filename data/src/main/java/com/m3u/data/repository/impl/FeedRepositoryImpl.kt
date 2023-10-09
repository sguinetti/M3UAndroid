package com.m3u.data.repository.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.core.net.toFile
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.execute
import com.m3u.core.architecture.sandBox
import com.m3u.core.util.belong
import com.m3u.core.wrapper.ProgressResource
import com.m3u.core.wrapper.emitException
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitProgress
import com.m3u.core.wrapper.emitResource
import com.m3u.data.R
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import com.m3u.data.parser.ConfusingFormatError
import com.m3u.data.parser.PlaylistParser
import com.m3u.data.parser.impl.toLive
import com.m3u.data.repository.FeedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    @Logger.Ui private val logger: Logger,
    configuration: Configuration,
    @PlaylistParser.Experimental private val parser: PlaylistParser,
    @ApplicationContext private val context: Context
) : FeedRepository {
    private val connectTimeout by configuration.connectTimeout

    override fun subscribe(
        title: String,
        url: String,
        strategy: Int
    ): Flow<ProgressResource<Unit>> = flow {
        try {
            val lives = when {
                url.startsWith("http://") || url.startsWith("https://") -> networkParse(url)
                url.startsWith("file://") || url.startsWith("content://") -> {
                    val uri = Uri.parse(url) ?: run {
                        emitMessage("Url is empty")
                        return@flow
                    }
                    val filename = when (uri.scheme) {
                        ContentResolver.SCHEME_FILE -> uri.toFile().name
                        ContentResolver.SCHEME_CONTENT -> {
                            context.contentResolver.query(
                                uri,
                                null,
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (index == -1) null
                                else cursor.getString(index)
                            } ?: "File_${System.currentTimeMillis()}.txt"
                        }

                        else -> ""
                    }
                    withContext(Dispatchers.IO) {
                        val content = when (uri.scheme) {
                            ContentResolver.SCHEME_FILE -> uri.toFile().readText()
                            ContentResolver.SCHEME_CONTENT ->
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.bufferedReader().readText()
                                }.orEmpty()

                            else -> ""
                        }
                        val file = File(context.filesDir, filename)
                        if (!file.exists()) {
                            file.createNewFile()
                        }
                        file.writeText(content)
                    }
                    diskParse(url)
                }

                else -> emptyList()
            }
            val feed = Feed(title, url)
            feedDao.insert(feed)
            val cachedLives = liveDao.getByFeedUrl(url)
            val skippedUrls = mutableListOf<String>()
            val groupedLives by lazy {
                cachedLives.groupBy { it.favourite }.withDefault { emptyList() }
            }
            val invalidateLives = when (strategy) {
                FeedStrategy.ALL -> cachedLives
                FeedStrategy.SKIP_FAVORITE -> groupedLives.getValue(false)
                else -> emptyList()
            }
            invalidateLives.forEach { live ->
                if (live belong lives) {
                    skippedUrls += live.url
                } else {
                    liveDao.deleteByUrl(live.url)
                }
            }
            val existedUrls = when (strategy) {
                FeedStrategy.ALL -> skippedUrls
                FeedStrategy.SKIP_FAVORITE -> groupedLives.getValue(true)
                    .map { it.url } + skippedUrls

                else -> emptyList()
            }
            var progress = 0
            lives
                .filterNot { it.url in existedUrls }
                .forEach {
                    liveDao.insert(it)
                    progress++
                    emitProgress(progress)
                }
            emitResource(Unit)
        } catch (e: ConfusingFormatError) {
            val feed = Feed("", Feed.URL_IMPORTED)
            val live = Live(
                url = url,
                group = "",
                title = title,
                feedUrl = feed.url
            )
            if (feedDao.getByUrl(feed.url) == null) {
                feedDao.insert(feed)
            }
            liveDao.insert(live)
            emitResource(Unit)
        } catch (e: FileNotFoundException) {
            error(context.getString(R.string.error_file_not_found))
        } catch (e: Exception) {
            logger.log(e)
            emitException(e)
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.of(connectTimeout.toLong(), ChronoUnit.MILLIS))
            .build()
    }

    @Throws(ConfusingFormatError::class)
    private suspend fun networkParse(url: String): List<Live> {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val input = response.body?.byteStream()
        return input?.use { parse(url, it) } ?: emptyList()
    }

    @Throws(ConfusingFormatError::class)
    private suspend fun diskParse(url: String): List<Live> {
        val uri = Uri.parse(url)
        val input = context.contentResolver.openInputStream(uri)
        return input?.let { parse(url, it) } ?: emptyList()
    }

    override fun observeAll(): Flow<List<Feed>> = logger.execute {
        feedDao.observeAll()
    } ?: flow { }

    override fun observe(url: String): Flow<Feed?> = logger.execute {
        feedDao.observeByUrl(url)
    } ?: flow { }

    override suspend fun get(url: String): Feed? = logger.execute {
        feedDao.getByUrl(url)
    }

    override suspend fun unsubscribe(url: String): Feed? = logger.execute {
        val feed = feedDao.getByUrl(url)
        liveDao.deleteByFeedUrl(url)
        feed?.also {
            feedDao.delete(it)
        }
    }

    private suspend fun parse(
        url: String,
        inputStream: InputStream
    ): List<Live> = parser
        .execute(inputStream)
        .map { it.toLive(url) }

    override suspend fun rename(url: String, target: String) = logger.sandBox {
        feedDao.rename(url, target)
    }
}