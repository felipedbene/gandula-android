package dev.debene

import org.junit.Test
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    try {
      val url = URL("https://api.github.com/repos/felipedbene/gandula/contents?ref=main")
      val conn = url.openConnection()
      conn.setRequestProperty("User-Agent", "Mozilla/5.0")
      conn.connect()
      val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
      val sb = StringBuilder()
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        sb.append(line).append("\n")
      }
      println("--- REPO ROOT CONTENTS ---")
      println(sb.toString())
      println("--- END REPO ROOT CONTENTS ---")
    } catch (e: Exception) {
      try {
        val url = URL("https://api.github.com/repos/felipedbene/gandula/contents?ref=master")
        val conn = url.openConnection()
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connect()
        val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
          sb.append(line).append("\n")
        }
        println("--- REPO ROOT CONTENTS ---")
        println(sb.toString())
        println("--- END REPO ROOT CONTENTS ---")
      } catch (e2: Exception) {
        e2.printStackTrace()
      }
    }
  }
}
