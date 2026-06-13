package dev.debene.gandula.data

import android.content.Context
import dev.debene.gandula.domain.Team
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Loads the bundled `teams.json` (an array of [Team]) from the app assets.
 * Moshi resolves the generated adapters for the data classes and its built-in
 * adapter for the enums (whose constant names match the JSON strings).
 */
object TeamRepository {
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, Team::class.java)
    private val adapter = moshi.adapter<List<Team>>(listType)

    fun loadTeams(context: Context): List<Team> {
        val json = context.assets.open("teams.json").bufferedReader().use { it.readText() }
        return adapter.fromJson(json) ?: emptyList()
    }
}
